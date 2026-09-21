package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.repository.*;
import com.juanpablo.evermail.util.SecurityUtil;
import lombok.Getter;
import java.nio.file.Path;
import java.time.Clock;

/** Backend composition root. It is deliberately not connected to App or any controller yet. */
@Getter
public class BackendContext implements AutoCloseable {
    private final TransactionManager transactions;
    private final KeyStoreService keys;
    private final AccountRepository accounts;
    private final MailRepository mails;
    private final OutboxRepository outbox;
    private final AuthService auth;
    private final InboxService inbox;
    private final MailSendService sender;
    private final StartupService startup;
    private final LegacyImporter legacy;

    public BackendContext(Path database, KeyStoreService keys, OAuthGateway oauth, EnvConfig config) throws EvermailException {
        this.keys = keys;
        SecurityUtil security = new SecurityUtil();
        transactions = new TransactionManager(new SqliteConnectionProvider(database));
        AccountCoordinator coordinator = new AccountCoordinator();
        accounts = new AccountRepository(transactions, security, keys);
        mails = new MailRepository(transactions, accounts, security, keys);
        outbox = new OutboxRepository(transactions, accounts, keys, security);
        auth = new AuthService(accounts, keys, oauth, config, coordinator, Clock.systemUTC());
        MailSessionProvider sessions = new MailSessionProvider(auth, accounts);
        inbox = new InboxService(sessions, mails, coordinator);
        sender = new MailSendService(sessions, outbox, new ComposeService(), coordinator);
        legacy = new LegacyImporter(transactions, accounts, security, keys);
        startup = new StartupService(new DatabaseMigrator(transactions), legacy, accounts, auth, sender);
    }

    public static BackendContext create() throws EvermailException {
        SecurityUtil security = new SecurityUtil();
        OsKeyStoreService keys = new OsKeyStoreService(security);
        try {
            return new BackendContext(StorageConfig.databasePath(), keys, new OAuthClient(), new EnvConfig());
        } catch (Exception e) {
            try {
                keys.close();
            } catch (Exception close) {
                e.addSuppressed(close);
            }
            if (e instanceof EvermailException domain) {
                throw domain;
            }
            throw new ConfigurationException(ErrorCode.CONFIG_INVALID, "Cannot initialize backend", e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            transactions.close();
        } finally {
            if (keys instanceof AutoCloseable resource) {
                resource.close();
            }
        }
    }
}
