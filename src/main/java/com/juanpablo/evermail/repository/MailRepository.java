package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.KeyStoreService;
import com.juanpablo.evermail.util.SecurityUtil;
import java.time.Instant;
import java.util.*;

public class MailRepository {
    private final TransactionManager transactions;
    private final AccountRepository accounts;
    private final SecurityUtil security;
    private final KeyStoreService keys;
    private final MailDAO mails = new MailDAO();
    private final RecipientDAO recipients = new RecipientDAO();
    private final InboxStateDAO states = new InboxStateDAO();

    public MailRepository(TransactionManager transactions, AccountRepository accounts, SecurityUtil security, KeyStoreService keys) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.security = security;
        this.keys = keys;
    }

    public InboxPage readInbox(UUID accountId, InboxCursor cursor, int size) throws EvermailException {
        accounts.require(accountId);
        if (size < 1 || size > 50 || (cursor != null && !cursor.getAccountId().equals(accountId))) {
            throw new MailFetchException(ErrorCode.CURSOR_INVALID, "Invalid page request");
        }
        return transactions.read(c -> {
            InboxStateDAO.Row state = states.find(c, accountId);
            if (state == null) {
                if (cursor != null) {
                    throw new MailFetchException(ErrorCode.CURSOR_INVALID, "Inbox generation is unavailable");
                }
                return new InboxPage(List.of(), null, true, true, true);
            }
            if (cursor != null && cursor.getUidValidity() != state.getValidity()) {
                throw new MailFetchException(ErrorCode.CURSOR_INVALID, "Inbox generation changed");
            }
            long before = cursor == null ? Long.MAX_VALUE : cursor.getBeforeUid();
            List<MailDAO.Row> rows = mails.page(c, accountId, state.getValidity(), before, state.getLower(), size + 1);
            boolean localMore = rows.size() > size;
            List<MailHeader> headers = rows.stream().limit(size).map(MailDAO.Row::getHeader).toList();
            boolean hasMore = localMore || state.isHasMore();
            long nextUid = headers.isEmpty() ? state.getLower() : headers.getLast().getRemoteId().getUid();
            InboxCursor next = hasMore ? new InboxCursor(accountId, state.getValidity(), nextUid) : null;
            boolean needsRemote = headers.size() < size && state.isHasMore();
            boolean stale = Instant.now().toEpochMilli() - state.getSyncedAt() > 60_000;
            return new InboxPage(headers, next, hasMore, stale, needsRemote);
        });
    }

    public void saveInboxPage(UUID accountId, RemoteInboxPage page) throws EvermailException {
        accounts.require(accountId);
        if (page.getUidValidity() < 1 || page.getLowerUid() < 1 || page.getUpperUid() < page.getLowerUid()) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Invalid remote page coverage");
        }
        transactions.write(c -> {
            InboxStateDAO.Row old = states.find(c, accountId);
            if (old != null && old.getValidity() != page.getUidValidity()) {
                Sql.update(c, "DELETE FROM mail WHERE id_account=? AND direction='INBOX'", accountId);
                old = null;
            }
            Set<Long> present = new HashSet<>();
            for (RemoteMessage message : page.getMessages()) {
                RemoteMailHeader h = message.getHeader();
                long uid = h.getRemoteId().getUid();
                if (h.getRemoteId().getUidValidity() != page.getUidValidity() || uid < page.getLowerUid() || uid > page.getUpperUid()) {
                    throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Message lies outside inspected range");
                }
                present.add(uid);
                MailDAO.Row existing = mails.findRemote(c, accountId, h.getRemoteId());
                UUID id = existing == null ? UUID.randomUUID() : existing.getHeader().getId();
                if (existing == null) {
                    mails.insert(c, new MailDAO.Row(new MailHeader(id, accountId, MailDirection.INBOX,
                            h.getRemoteId(), h.getMessageId(), null, h.getSenderEmail(), h.getSenderName(),
                            h.getSubject() == null ? "" : h.getSubject(), h.getOccurredAt(), false, false), null));
                } else {
                    Sql.update(c, "UPDATE mail SET subject=?,sender_name=? WHERE id_mail=?",
                            h.getSubject() == null ? "" : h.getSubject(), h.getSenderName(), id);
                }
                recipients.replace(c, id, message.getRecipients(), false);
            }
            List<MailDAO.Row> inspected = mails.page(c, accountId, page.getUidValidity(),
                    page.getUpperUid() == Long.MAX_VALUE ? Long.MAX_VALUE : page.getUpperUid() + 1,
                    page.getLowerUid(), Integer.MAX_VALUE);
            for (MailDAO.Row row : inspected) {
                if (!present.contains(row.getHeader().getRemoteId().getUid())) {
                    Sql.update(c, "DELETE FROM mail WHERE id_mail=?", row.getHeader().getId());
                }
            }
            long lower = page.getLowerUid();
            long upper = page.getUpperUid();
            boolean more = page.isHasMore();
            if (old != null && lower <= old.getUpper() + 1 && upper >= old.getLower() - 1) {
                lower = Math.min(lower, old.getLower());
                upper = Math.max(upper, old.getUpper());
                more = page.getLowerUid() <= old.getLower() ? page.isHasMore() : old.isHasMore();
            }
            states.save(c, accountId, new InboxStateDAO.Row(page.getUidValidity(), lower, upper, more, System.currentTimeMillis()));
            return null;
        });
    }

    public MailHeader findHeader(UUID accountId, UUID mailId) throws EvermailException {
        accounts.require(accountId);
        MailDAO.Row row = transactions.read(c -> mails.find(c, accountId, mailId));
        if (row == null) {
            throw new MailFetchException(ErrorCode.MAIL_NOT_FOUND, "Mail is unavailable");
        }
        return row.getHeader();
    }

    public MailContent readContent(UUID accountId, UUID mailId) throws EvermailException {
        Account account = accounts.require(accountId);
        MailDAO.Row row = transactions.read(c -> mails.find(c, accountId, mailId));
        if (row == null) {
            throw new MailFetchException(ErrorCode.MAIL_NOT_FOUND, "Mail is unavailable");
        }
        if (row.getBodyCipher() == null) {
            return null;
        }
        String decoded = security.decrypt(row.getBodyCipher(), keys.read(account.getKeyRef()),
                new CryptoContext(accountId, mailId, row.getBodyFormat() == 0 ? "body" : "body:hybrid-v1"));
        var addresses = transactions.read(c -> recipients.find(c, mailId, false));
        if (row.getBodyFormat() == 0) {
            return new MailContent(mailId, decoded, null, false,
                    row.getHeader().getDirection() == MailDirection.INBOX, addresses);
        }
        try {
            StoredBody body = new com.google.gson.Gson().fromJson(decoded, StoredBody.class);
            if (body == null || body.plainText() == null) throw new IllegalArgumentException("Invalid body");
            return new MailContent(mailId, body.plainText(), body.html(), body.blockedRemoteImages(), false, addresses);
        } catch (RuntimeException error) {
            throw new com.juanpablo.evermail.exception.DatabaseException(ErrorCode.DB_QUERY_FAILED, "Cannot decode cached body", error);
        }
    }

    public void saveContent(UUID accountId, UUID mailId, RemoteMailContent content) throws EvermailException {
        Account account = accounts.require(accountId);
        String serialized = new com.google.gson.Gson().toJson(new StoredBody(content.getPlainText(), content.getHtml(), content.isBlockedRemoteImages()));
        String encrypted = security.encrypt(serialized, keys.read(account.getKeyRef()),
                new CryptoContext(accountId, mailId, "body:hybrid-v1"));
        transactions.write(c -> {
            if (Sql.update(c, "UPDATE mail SET body_cipher=?,body_format=1 WHERE id_account=? AND id_mail=?", encrypted, accountId, mailId) != 1) {
                throw new MailFetchException(ErrorCode.MAIL_NOT_FOUND, "Mail disappeared during download");
            }
            recipients.replace(c, mailId, content.getRecipients(), false);
            return null;
        });
    }

    public void markRead(UUID accountId, UUID mailId) throws EvermailException {
        accounts.require(accountId);
        transactions.write(c -> {
            if (Sql.update(c, "UPDATE mail SET is_read=1 WHERE id_account=? AND id_mail=?", accountId, mailId) != 1) {
                throw new MailFetchException(ErrorCode.MAIL_NOT_FOUND, "Mail is unavailable");
            }
            return null;
        });
    }

    private record StoredBody(String plainText, String html, boolean blockedRemoteImages) { }

    public void remove(UUID accountId, UUID mailId) throws EvermailException {
        transactions.write(c -> {
            Sql.update(c, "DELETE FROM mail WHERE id_account=? AND id_mail=? AND direction='INBOX'", accountId, mailId);
            return null;
        });
    }
}
