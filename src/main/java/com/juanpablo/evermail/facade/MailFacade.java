package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Label;
import com.juanpablo.evermail.model.Mail;
import com.juanpablo.evermail.service.MailSyncService;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Exposure layer for inbox synchronization and read-state.
 */
public class MailFacade {

    private final MailSyncService mailSyncService;

    public MailFacade(MailSyncService mailSyncService) {
        this.mailSyncService = mailSyncService;
    }

    public Task<List<Mail>> syncInboxTask(Account account) {
        return new Task<>() {
            @Override
            protected List<Mail> call() throws Exception {
                return mailSyncService.syncInbox(account);
            }
        };
    }

    public Task<Void> markAsReadTask(Mail mail, Label label) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                mailSyncService.markAsRead(mail, label);
                return null;
            }
        };
    }
}