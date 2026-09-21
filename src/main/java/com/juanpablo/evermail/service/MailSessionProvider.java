package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.AccountRepository;
import com.juanpablo.evermail.util.MimeUtil;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class MailSessionProvider implements MailGateway {
    private final AuthService auth;
    private final AccountRepository accounts;
    private static final ScheduledExecutorService CLOSER = Executors.newScheduledThreadPool(2,
            Thread.ofPlatform().daemon().name("evermail-deadline-", 0).factory());

    public MailSessionProvider(AuthService auth, AccountRepository accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    public static Properties imapProperties(int timeoutMillis) {
        Properties properties = new Properties();
        properties.setProperty("mail.imaps.auth.mechanisms", "XOAUTH2");
        properties.setProperty("mail.imaps.ssl.enable", "true");
        properties.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        properties.setProperty("mail.imaps.peek", "true");
        timeouts(properties, "imaps", timeoutMillis);
        return properties;
    }

    public static Properties smtpProperties(int timeoutMillis) {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth.mechanisms", "XOAUTH2");
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.sendpartial", "false");
        properties.setProperty("mail.smtp.quitwait", "false");
        timeouts(properties, "smtp", timeoutMillis);
        return properties;
    }

    private static void timeouts(Properties properties, String protocol, int timeoutMillis) {
        for (String option : List.of("connectiontimeout", "timeout", "writetimeout")) {
            properties.setProperty("mail." + protocol + "." + option, String.valueOf(Math.max(1, timeoutMillis)));
        }
    }

    @Override
    public InboxSession openInbox(UUID accountId, Deadline deadline) throws EvermailException {
        OAuthCredentials credentials = auth.ensureCredentials(accountId, deadline);
        Account account = accounts.require(accountId);
        Store store = null;
        ScheduledFuture<?> closer = null;
        try {
            store = Session.getInstance(imapProperties(deadline.remainingMillis())).getStore("imaps");
            Store toClose = store;
            closer = CLOSER.schedule(() -> closeStore(toClose), deadline.remainingMillis(), TimeUnit.MILLISECONDS);
            store.connect(account.getProvider().getImapHost(), account.getProvider().getImapPort(),
                    account.getEmail(), credentials.getAccessToken());
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            if (!(folder instanceof UIDFolder uidFolder)) {
                throw new MessagingException("Server has no UID support");
            }
            deadline.check();
            return new ImapInbox(store, folder, uidFolder, closer);
        } catch (Exception e) {
            if (closer != null) {
                closer.cancel(false);
            }
            closeStore(store);
            if (e instanceof EvermailException domain) {
                throw domain;
            }
            throw new MailFetchException(ErrorCode.IMAP_CONNECTION_FAILED, "Unable to connect to inbox", e);
        }
    }

    @Override
    public SmtpSession openSmtp(UUID accountId, Deadline deadline) throws EvermailException {
        OAuthCredentials credentials = auth.ensureCredentials(accountId, deadline);
        Account account = accounts.require(accountId);
        Transport transport = null;
        ScheduledFuture<?> closer = null;
        try {
            Session session = Session.getInstance(smtpProperties(deadline.remainingMillis()));
            transport = session.getTransport("smtp");
            Transport toClose = transport;
            closer = CLOSER.schedule(() -> closeTransport(toClose), deadline.remainingMillis(), TimeUnit.MILLISECONDS);
            transport.connect(account.getProvider().getSmtpHost(), account.getProvider().getSmtpPort(),
                    account.getEmail(), credentials.getAccessToken());
            deadline.check();
            return new SmtpTransport(session, transport, account.getEmail(), closer);
        } catch (Exception e) {
            if (closer != null) {
                closer.cancel(false);
            }
            closeTransport(transport);
            if (e instanceof EvermailException domain) {
                throw domain;
            }
            throw new MailSendException(ErrorCode.SMTP_CONNECTION_FAILED, "Unable to connect to outgoing mail", e);
        }
    }

    private static void closeStore(Store store) {
        try {
            if (store != null) {
                store.close();
            }
        } catch (Exception ignored) {
            // Socket timeout remains the final bound if a close races with I/O.
        }
    }

    private static void closeTransport(Transport transport) {
        try {
            if (transport != null) {
                transport.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static final class ImapInbox implements InboxSession {
        private final Store store;
        private final Folder folder;
        private final UIDFolder uids;
        private final ScheduledFuture<?> closer;

        private ImapInbox(Store store, Folder folder, UIDFolder uids, ScheduledFuture<?> closer) {
            this.store = store;
            this.folder = folder;
            this.uids = uids;
            this.closer = closer;
        }

        @Override
        public RemoteInboxPage fetchLatest(int size, Deadline deadline) throws EvermailException {
            try {
                return page(folder.getMessageCount(), Long.MAX_VALUE, size, deadline);
            } catch (Exception e) {
                throw fetchError(e);
            }
        }

        @Override
        public RemoteInboxPage fetchBefore(InboxCursor cursor, int size, Deadline deadline) throws EvermailException {
            try {
                if (cursor.getUidValidity() != uids.getUIDValidity()) {
                    throw new MailFetchException(ErrorCode.CURSOR_INVALID, "Mailbox generation changed");
                }
                Message anchor = uids.getMessageByUID(cursor.getBeforeUid());
                if (anchor != null && !anchor.isExpunged()) {
                    return page(anchor.getMessageNumber() - 1, cursor.getBeforeUid(), size, deadline);
                }
                // Deleted anchor: binary search sequence numbers without materializing the mailbox.
                int low = 1;
                int high = folder.getMessageCount();
                int end = 0;
                while (low <= high) {
                    deadline.check();
                    int middle = low + (high - low) / 2;
                    long uid = uids.getUID(folder.getMessage(middle));
                    if (uid < cursor.getBeforeUid()) {
                        end = middle;
                        low = middle + 1;
                    } else {
                        high = middle - 1;
                    }
                }
                return page(end, cursor.getBeforeUid(), size, deadline);
            } catch (Exception e) {
                throw fetchError(e);
            }
        }

        private RemoteInboxPage page(int end, long before, int size, Deadline deadline) throws Exception {
            deadline.check();
            long validity = uids.getUIDValidity();
            long upper = before == Long.MAX_VALUE
                    ? (end == 0 ? Math.max(1, uids.getUIDNext() - 1) : uids.getUID(folder.getMessage(end)))
                    : Math.max(1, before - 1);
            if (end == 0) {
                return new RemoteInboxPage(validity, 1, upper, List.of(), false);
            }
            int start = Math.max(1, end - size + 1);
            Message[] messages = folder.getMessages(start, end);
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(UIDFolder.FetchProfileItem.UID);
            profile.add("Message-ID");
            folder.fetch(messages, profile);
            List<RemoteMessage> result = new ArrayList<>();
            long lower = upper;
            for (int i = messages.length - 1; i >= 0; i--) {
                deadline.check();
                Message message = messages[i];
                if (message.isExpunged()) {
                    throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Inbox changed while loading; refresh again");
                }
                long uid = uids.getUID(message);
                lower = Math.min(lower, uid);
                Address[] from = message.getFrom();
                InternetAddress sender = from != null && from.length > 0 && from[0] instanceof InternetAddress a ? a : null;
                Date date = message.getReceivedDate();
                String[] ids = message.getHeader("Message-ID");
                result.add(new RemoteMessage(new RemoteMailHeader(new RemoteMailId(validity, uid),
                        ids == null || ids.length == 0 ? null : ids[0],
                        sender == null ? null : sender.getAddress(), sender == null ? null : sender.getPersonal(),
                        message.getSubject() == null ? "" : message.getSubject(),
                        date == null ? Instant.now() : date.toInstant()), MimeUtil.extractRecipients(message)));
            }
            return new RemoteInboxPage(validity, start == 1 ? 1 : lower, upper, result, start > 1);
        }

        @Override
        public RemoteMailContent fetchContent(RemoteMailId id, Deadline deadline) throws EvermailException {
            try {
                deadline.check();
                if (id.getUidValidity() != uids.getUIDValidity()) {
                    throw new MailFetchException(ErrorCode.CURSOR_INVALID, "Mailbox generation changed");
                }
                Message message = uids.getMessageByUID(id.getUid());
                if (message == null || message.isExpunged()) {
                    throw new MailFetchException(ErrorCode.MAIL_NOT_FOUND, "Message no longer exists in inbox");
                }
                String text = MimeUtil.extractReadableText(message);
                deadline.check();
                return new RemoteMailContent(text, MimeUtil.extractRecipients(message));
            } catch (Exception e) {
                throw fetchError(e);
            }
        }

        private EvermailException fetchError(Exception e) {
            return e instanceof EvermailException domain ? domain
                    : new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Inbox operation failed", e);
        }

        @Override
        public void close() {
            closer.cancel(false);
            closeStore(store);
        }
    }

    private static final class SmtpTransport implements SmtpSession {
        private final Session session;
        private final Transport transport;
        private final String sender;
        private final ScheduledFuture<?> closer;

        private SmtpTransport(Session session, Transport transport, String sender, ScheduledFuture<?> closer) {
            this.session = session;
            this.transport = transport;
            this.sender = sender;
            this.closer = closer;
        }

        @Override
        public Outcome submit(OutboxMessage outgoing, Deadline deadline) throws EvermailException {
            try {
                deadline.check();
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(sender));
                List<Address> envelope = new ArrayList<>();
                for (Recipient recipient : outgoing.getRecipients()) {
                    InternetAddress address = new InternetAddress(recipient.getEmail());
                    envelope.add(address);
                    if (recipient.getType() != RecipientType.BCC) {
                        message.addRecipient(recipient.getType() == RecipientType.TO ? Message.RecipientType.TO : Message.RecipientType.CC, address);
                    }
                }
                message.setSubject(outgoing.getSubject(), "UTF-8");
                message.setText(outgoing.getPlainText(), "UTF-8");
                message.setSentDate(new Date());
                message.saveChanges();
                message.setHeader("Message-ID", outgoing.getMessageId());
                deadline.check();
                transport.sendMessage(message, envelope.toArray(Address[]::new));
                return Outcome.ACCEPTED;
            } catch (SendFailedException e) {
                if (e.getValidSentAddresses() != null && e.getValidSentAddresses().length > 0) {
                    return Outcome.UNKNOWN;
                }
                // Only explicit recipient rejection is certain. DATA/transport errors remain uncertain.
                if (e.getInvalidAddresses() != null && e.getInvalidAddresses().length > 0) {
                    return Outcome.REJECTED;
                }
                return Outcome.UNKNOWN;
            } catch (Exception e) {
                return Outcome.UNKNOWN;
            }
        }

        @Override
        public void close() {
            closer.cancel(false);
            closeTransport(transport);
        }
    }
}