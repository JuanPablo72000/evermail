package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.service.InboxService;
import javafx.concurrent.Task;
import java.util.UUID;

/** Creates backend tasks without starting them or changing any screen. */
public class MailFacade {
    private final InboxService inbox;

    public MailFacade(InboxService inbox) {
        this.inbox = inbox;
    }

    public Task<InboxPage> cachedInboxTask(UUID accountId, InboxCursor cursor) {
        Deadline deadline = Deadline.after(AppConstants.INBOX_BUDGET);
        return new Task<>() {
            @Override
            protected InboxPage call() throws Exception {
                deadline.check();
                return inbox.readCached(accountId, cursor);
            }
        };
    }

    public Task<InboxPage> refreshInboxTask(UUID accountId) {
        Deadline deadline = Deadline.after(AppConstants.INBOX_BUDGET);
        return new Task<>() {
            @Override
            protected InboxPage call() throws Exception {
                deadline.check();
                return inbox.refresh(accountId, deadline);
            }
        };
    }

    public Task<InboxPage> loadMoreTask(UUID accountId, InboxCursor cursor) {
        Deadline deadline = Deadline.after(AppConstants.INBOX_BUDGET);
        return new Task<>() {
            @Override
            protected InboxPage call() throws Exception {
                deadline.check();
                return inbox.loadMore(accountId, cursor, deadline);
            }
        };
    }

    public Task<MailHeader> openHeaderTask(UUID accountId, UUID mailId) {
        Deadline deadline = Deadline.after(AppConstants.OPEN_HEADER_BUDGET);
        return new Task<>() {
            @Override
            protected MailHeader call() throws Exception {
                deadline.check();
                return inbox.openHeader(accountId, mailId);
            }
        };
    }

    public Task<MailContent> loadContentTask(UUID accountId, UUID mailId) {
        Deadline deadline = Deadline.after(AppConstants.CONTENT_BUDGET);
        return new Task<>() {
            @Override
            protected MailContent call() throws Exception {
                deadline.check();
                return inbox.loadContent(accountId, mailId, deadline);
            }
        };
    }

    public Task<Void> markReadTask(UUID accountId, UUID mailId) {
        Deadline deadline = Deadline.after(AppConstants.OPEN_HEADER_BUDGET);
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                deadline.check();
                inbox.markRead(accountId, mailId);
                return null;
            }
        };
    }
}
