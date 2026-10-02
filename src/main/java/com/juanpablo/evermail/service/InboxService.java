package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.MailRepository;
import java.util.UUID;

public class InboxService {
    private final MailGateway gateway;
    private final MailRepository mails;
    private final AccountCoordinator coordinator;

    public InboxService(MailGateway gateway, MailRepository mails, AccountCoordinator coordinator) {
        this.gateway = gateway;
        this.mails = mails;
        this.coordinator = coordinator;
    }

    public InboxPage readCached(UUID accountId, InboxCursor cursor) throws EvermailException {
        return mails.readInbox(accountId, cursor, AppConstants.MAX_EMAILS_DISPLAYED);
    }

    public InboxPage refresh(UUID accountId, Deadline deadline) throws EvermailException {
        return coordinator.exclusive(accountId, deadline, () -> {
            try (InboxSession session = gateway.openInbox(accountId, deadline)) {
                RemoteInboxPage page = session.fetchLatest(AppConstants.MAX_EMAILS_DISPLAYED, deadline);
                deadline.check();
                mails.saveInboxPage(accountId, page);
                return readCached(accountId, null);
            }
        });
    }

    public InboxPage loadMore(UUID accountId, InboxCursor cursor, Deadline deadline) throws EvermailException {
        deadline.check();
        if (cursor == null || !cursor.getAccountId().equals(accountId)) {
            throw new MailFetchException(ErrorCode.CURSOR_INVALID, "A cursor for this account is required");
        }
        InboxPage cached = readCached(accountId, cursor);
        deadline.check();
        if (!cached.isNeedsRemote()) {
            return cached;
        }
        return coordinator.exclusive(accountId, deadline, () -> {
            try (InboxSession session = gateway.openInbox(accountId, deadline)) {
                RemoteInboxPage page = session.fetchBefore(cursor, AppConstants.MAX_EMAILS_DISPLAYED, deadline);
                deadline.check();
                deadline.check();
                mails.saveInboxPage(accountId, page);
                return readCached(accountId, cursor);
            }
        });
    }

    public MailHeader openHeader(UUID accountId, UUID mailId) throws EvermailException {
        return mails.findHeader(accountId, mailId);
    }

    public MailContent loadContent(UUID accountId, UUID mailId, Deadline deadline) throws EvermailException {
        return loadContent(accountId, mailId, deadline, false);
    }

    /** Explicit upgrade/retry only: ordinary opens never discard offline legacy text. */
    public MailContent reloadContent(UUID accountId, UUID mailId, Deadline deadline) throws EvermailException {
        return loadContent(accountId, mailId, deadline, true);
    }

    private MailContent loadContent(UUID accountId, UUID mailId, Deadline deadline, boolean reload) throws EvermailException {
        deadline.check();
        MailContent cached = mails.readContent(accountId, mailId);
        deadline.check();
        if (cached != null && !reload) {
            return cached;
        }
        return coordinator.exclusive(accountId, deadline, () -> {
            MailContent again = mails.readContent(accountId, mailId);
            deadline.check();
            if (again != null && !reload) {
                return again;
            }
            MailHeader header = mails.findHeader(accountId, mailId);
            if (header.getRemoteId() == null) {
                throw new MailFetchException(ErrorCode.MAIL_NOT_FOUND, "Sent content is unavailable");
            }
            try (InboxSession session = gateway.openInbox(accountId, deadline)) {
                RemoteMailContent content = session.fetchContent(header.getRemoteId(), deadline);
                deadline.check();
                mails.saveContent(accountId, mailId, content);
                deadline.check();
                MailContent result = mails.readContent(accountId, mailId);
                deadline.check();
                return result;
            } catch (MailFetchException e) {
                if (e.getErrorCode() == ErrorCode.MAIL_NOT_FOUND) {
                    mails.remove(accountId, mailId);
                }
                throw e;
            }
        });
    }

    public void markRead(UUID accountId, UUID mailId) throws EvermailException {
        mails.markRead(accountId, mailId);
    }
}
