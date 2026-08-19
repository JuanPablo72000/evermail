package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Draft;
import com.juanpablo.evermail.model.DraftAddress;
import com.juanpablo.evermail.model.Mail;
import com.juanpablo.evermail.service.DraftService;
import com.juanpablo.evermail.service.MailSendService;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Exposure layer for the whole "mail composition" sub-domain (create, edit,
 * discard, send). The compose controller only knows this facade, never the
 * two underlying services separately.
 */
public class ComposeFacade {

    private final DraftService draftService;
    private final MailSendService mailSendService;

    public ComposeFacade(DraftService draftService, MailSendService mailSendService) {
        this.draftService = draftService;
        this.mailSendService = mailSendService;
    }

    public Task<Draft> createDraftTask(Account account) {
        return new Task<>() {
            @Override
            protected Draft call() throws Exception {
                return draftService.createDraft(account);
            }
        };
    }

    public Task<DraftAddress> resolveRecipientTask(String email, String recipientType) {
        return new Task<>() {
            @Override
            protected DraftAddress call() throws Exception {
                return draftService.resolveRecipient(email, recipientType);
            }
        };
    }

    public Task<Void> updateDraftTask(Draft draft, List<DraftAddress> recipients) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                draftService.updateDraft(draft, recipients);
                return null;
            }
        };
    }

    public Task<Void> discardDraftTask(Draft draft) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                draftService.discardDraft(draft);
                return null;
            }
        };
    }

    public Task<Mail> sendDraftTask(Draft draft, List<String> to, List<String> cc, List<String> bcc) {
        return new Task<>() {
            @Override
            protected Mail call() throws Exception {
                return mailSendService.sendDraft(draft, to, cc, bcc);
            }
        };
    }
}