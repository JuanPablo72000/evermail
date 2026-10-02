package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class AuthService {
    private final AccountRepository accounts;
    private final KeyStoreService keys;
    private final OAuthGateway oauth;
    private final EnvConfig config;
    private final AccountCoordinator coordinator;
    private final Clock clock;

    public AuthService(AccountRepository accounts, KeyStoreService keys, OAuthGateway oauth,
                       EnvConfig config, AccountCoordinator coordinator, Clock clock) {
        this.accounts = accounts;
        this.keys = keys;
        this.oauth = oauth;
        this.config = config;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    public synchronized Account login(OAuthProvider provider, CancellationToken cancellation) throws EvermailException {
        AuthorizationResult result = oauth.authorize(config.loadProvider(provider), cancellation);
        cancellation.check();
        Account found = accounts.findByIdentity(result.getIdentity());
        if (found == null) {
            found = accounts.findByIdentity(new Identity(provider, "legacy:" + result.getIdentity().getEmail(),
                    result.getIdentity().getEmail(), result.getIdentity().getDisplayName()));
        }
        final Account existing = found;
        if (existing != null) {
            return coordinator.exclusive(existing.getId(), Deadline.after(AppConstants.STARTUP_BUDGET), () -> {
                Account current = accounts.require(existing.getId());
                OAuthCredentials tokens = result.getCredentials();
                if (tokens.getRefreshToken() == null || tokens.getRefreshToken().isBlank()) {
                    tokens = tokens.withRefreshToken(accounts.readCredentials(current.getId()).getRefreshToken());
                }
                Account updated = current.withProviderSubject(result.getIdentity().getSubject()).withEmail(result.getIdentity().getEmail()).withDisplayName(result.getIdentity().getDisplayName());
                accounts.activate(updated, tokens);
                return updated.withStatus(AccountStatus.ACTIVE);
            });
        }
        if (result.getCredentials().getRefreshToken() == null || result.getCredentials().getRefreshToken().isBlank()) {
            throw new OAuthAuthenticationException(ErrorCode.OAUTH_INVALID_CREDENTIALS, "Offline authorization is required");
        }
        Account account = accounts.beginProvisioning(result.getIdentity());
        try {
            keys.create(account.getKeyRef());
            accounts.activate(account, result.getCredentials());
            return account.withStatus(AccountStatus.ACTIVE);
        } catch (EvermailException e) {
            try {
                keys.delete(account.getKeyRef());
                accounts.deleteLocal(account.getId());
            } catch (EvermailException cleanup) {
                e.addSuppressed(cleanup);
                // PROVISIONING remains recoverable at the next startup.
            }
            throw e;
        }
    }

    public OAuthCredentials ensureCredentials(UUID id, Deadline deadline) throws EvermailException {
        return coordinator.exclusive(id, deadline, () -> {
            Account account = accounts.require(id);
            if (account.getStatus() == AccountStatus.REAUTH_REQUIRED) {
                throw new OAuthAuthenticationException(ErrorCode.REAUTH_REQUIRED, "Please authorize this account again");
            }
            OAuthCredentials credentials = accounts.readCredentials(id);
            if (credentials.getExpiresAt().isAfter(Instant.now(clock).plus(AppConstants.TOKEN_REFRESH_MARGIN))) {
                return credentials;
            }
            try {
                OAuthCredentials updated = oauth.refresh(config.loadProvider(account.getProvider()), credentials, deadline);
                accounts.activate(account, updated);
                return updated;
            } catch (OAuthAuthenticationException e) {
                if (e.getErrorCode() == ErrorCode.REAUTH_REQUIRED) {
                    accounts.setStatus(id, AccountStatus.REAUTH_REQUIRED);
                }
                throw e;
            }
        });
    }

    public StartupResult restoreSession(Deadline deadline) throws EvermailException {
        deadline.check();
        for (Account account : accounts.list()) {
            deadline.check();
            if (account.getStatus() == AccountStatus.ACTIVE) {
                keys.read(account.getKeyRef());
                // Restoring local state never waits for network; sessions refresh on demand.
                OAuthCredentials credentials = accounts.readCredentials(account.getId());
                StartupStatus status = credentials.getExpiresAt().isAfter(Instant.now(clock))
                        ? StartupStatus.READY : StartupStatus.OFFLINE;
                deadline.check();
                return new StartupResult(account, status);
            }
            if (account.getStatus() == AccountStatus.REAUTH_REQUIRED) {
                keys.read(account.getKeyRef());
                deadline.check();
                return new StartupResult(account, StartupStatus.OFFLINE);
            }
        }
        deadline.check();
        return new StartupResult(null, StartupStatus.LOGIN_REQUIRED);
    }

    public void logout(UUID id) throws EvermailException {
        logout(id, Deadline.after(AppConstants.STARTUP_BUDGET));
    }

    public void logout(UUID id, Deadline deadline) throws EvermailException {
        coordinator.exclusive(id, deadline, () -> {
            Account account = accounts.find(id);
            if (account == null) {
                return null;
            }
            accounts.setStatus(id, AccountStatus.DISCONNECTING);
            keys.delete(account.getKeyRef());
            String legacyRef = accounts.legacyKeyRef(id);
            if (legacyRef != null) {
                keys.delete(legacyRef);
            }
            accounts.deleteLocal(id);
            return null;
        });
    }

    public void recoverAccountLifecycle() throws EvermailException {
        recoverAccountLifecycle(Deadline.after(AppConstants.STARTUP_BUDGET));
    }

    public void recoverAccountLifecycle(Deadline deadline) throws EvermailException {
        deadline.check();
        for (Account account : accounts.list()) {
            deadline.check();
            if (account.getStatus() == AccountStatus.PROVISIONING || account.getStatus() == AccountStatus.DISCONNECTING) {
                logout(account.getId(), deadline);
            }
        }
        deadline.check();
    }
}
