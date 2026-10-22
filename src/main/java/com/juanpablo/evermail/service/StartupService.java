package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.EvermailException;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.*;

public class StartupService {
    private final DatabaseMigrator migrator;
    private final LegacyImporter legacy;
    private final AccountRepository accounts;
    private final AuthService auth;
    private final MailSendService sender;

    public StartupService(DatabaseMigrator migrator, LegacyImporter legacy, AccountRepository accounts,
                          AuthService auth, MailSendService sender) {
        this.migrator = migrator;
        this.legacy = legacy;
        this.accounts = accounts;
        this.auth = auth;
        this.sender = sender;
    }

    public StartupResult start(Deadline deadline) throws EvermailException {
        deadline.check();
        migrator.migrate();
        deadline.check();
        auth.recoverAccountLifecycle(deadline);
        deadline.check();
        legacy.importAccounts(deadline);
        deadline.check();
        for (Account account : accounts.list()) {
            deadline.check();
            if (account.getStatus() == AccountStatus.ACTIVE || account.getStatus() == AccountStatus.REAUTH_REQUIRED) {
                sender.recover(account.getId(), deadline);
            }
        }
        deadline.check();
        StartupResult result = auth.restoreSession(deadline);
        deadline.check();
        return result;
    }
}
