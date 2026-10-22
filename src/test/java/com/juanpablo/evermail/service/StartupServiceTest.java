package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Unit tests of startup orchestration; persistence and OAuth are replaced by local doubles. */
class StartupServiceTest {
    private final ManualDeadline clock = new ManualDeadline(Duration.ofSeconds(5));
    private final List<String> calls = new ArrayList<>();
    private final List<Deadline> receivedBudgets = new ArrayList<>();
    private String expireAfter;
    private String failAt;
    private final List<Account> accounts = new ArrayList<>();

    private Account account(AccountStatus status) {
        return new Account(UUID.randomUUID(), OAuthProvider.GOOGLE, "subject", "user@example.com",
                "User", "test-key", status);
    }

    private void step(String name) throws EvermailException {
        calls.add(name);
        if (name.equals(failAt)) {
            throw new DatabaseException(ErrorCode.DB_QUERY_FAILED, "Test failure");
        }
        if (name.equals(expireAfter)) clock.expire();
    }

    private StartupService service() {
        DatabaseMigrator migrator = new DatabaseMigrator(null) {
            @Override public void migrate() throws EvermailException { step("migrate"); }
        };
        LegacyImporter legacy = new LegacyImporter(null, null, null, null) {
            @Override public void importAccounts(Deadline deadline) throws EvermailException {
                receivedBudgets.add(deadline);
                step("legacy");
            }
        };
        AccountRepository repository = new AccountRepository(null, null, null) {
            @Override public List<Account> list() throws EvermailException {
                step("accounts");
                return List.copyOf(accounts);
            }
        };
        AuthService auth = new AuthService(null, null, null, null, null, null) {
            @Override public void recoverAccountLifecycle(Deadline deadline) throws EvermailException {
                receivedBudgets.add(deadline);
                step("lifecycle");
            }
            @Override public StartupResult restoreSession(Deadline deadline) throws EvermailException {
                receivedBudgets.add(deadline);
                step("restore");
                return new StartupResult(null, StartupStatus.LOGIN_REQUIRED);
            }
        };
        MailSendService sender = new MailSendService(null, null, null, null) {
            @Override public void recover(UUID id, Deadline deadline) throws EvermailException {
                receivedBudgets.add(deadline);
                step("outbox:" + id);
            }
        };
        return new StartupService(migrator, legacy, repository, auth, sender);
    }

    @Test
    void sharesOneBudgetAndRecoversOnlyUsableAccountsBeforeRestoringSession() throws Exception {
        Account active = account(AccountStatus.ACTIVE);
        Account reauth = account(AccountStatus.REAUTH_REQUIRED);
        accounts.addAll(List.of(active, reauth, account(AccountStatus.PROVISIONING), account(AccountStatus.DISCONNECTING)));
        assertEquals(StartupStatus.LOGIN_REQUIRED, service().start(clock.deadline()).getStatus());
        assertEquals(List.of("migrate", "lifecycle", "legacy", "accounts",
                "outbox:" + active.getId(), "outbox:" + reauth.getId(), "restore"), calls);
        assertEquals(5, receivedBudgets.size());
        receivedBudgets.forEach(deadline -> assertSame(clock.deadline(), deadline));
    }

    @Test
    void expiredStartupDoesNotBeginMigration() {
        clock.expire();
        assertEquals(ErrorCode.LOCAL_TIMEOUT,
                assertThrows(SessionException.class, () -> service().start(clock.deadline())).getErrorCode());
        assertTrue(calls.isEmpty());
    }

    @Test
    void lifecycleExhaustionPreventsLegacyImport() {
        expireAfter = "lifecycle";
        assertThrows(SessionException.class, () -> service().start(clock.deadline()));
        assertEquals(List.of("migrate", "lifecycle"), calls);
    }

    @Test
    void recoveryExhaustionStopsBeforeTheNextAccountAndSessionRestore() {
        Account first = account(AccountStatus.ACTIVE);
        accounts.addAll(List.of(first, account(AccountStatus.ACTIVE)));
        expireAfter = "outbox:" + first.getId();
        assertThrows(SessionException.class, () -> service().start(clock.deadline()));
        assertEquals(List.of("migrate", "lifecycle", "legacy", "accounts", expireAfter), calls);
    }

    @Test
    void doesNotReturnSuccessWhenSessionRestoreExhaustsTheBudget() {
        expireAfter = "restore";
        assertEquals(ErrorCode.LOCAL_TIMEOUT,
                assertThrows(SessionException.class, () -> service().start(clock.deadline())).getErrorCode());
        assertEquals("restore", calls.getLast());
    }

    @Test
    void migrationFailureStopsStartupAndPreservesTheOriginalError() {
        failAt = "migrate";
        assertEquals(ErrorCode.DB_QUERY_FAILED,
                assertThrows(DatabaseException.class, () -> service().start(clock.deadline())).getErrorCode());
        assertEquals(List.of("migrate"), calls);
    }
}
