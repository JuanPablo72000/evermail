package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.MailFetchException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Attachment;
import com.juanpablo.evermail.model.EmailAddress;
import com.juanpablo.evermail.model.Label;
import com.juanpablo.evermail.model.Mail;
import com.juanpablo.evermail.model.MailAddress;
import com.juanpablo.evermail.repository.AccountRepository;
import com.juanpablo.evermail.repository.MailRepository;
import com.juanpablo.evermail.util.MimeUtil;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Synchronizes the remote IMAP inbox into the local SQLite cache and owns
 * the read/unread state transition. Sync is incremental: messages whose
 * Message-ID already exists locally are skipped, so re-syncing never
 * duplicates rows.
 *
 * <p>Attachment binaries are NOT downloaded here (on-demand download is a
 * documented MVP decision) — only their metadata (name, MIME type, size) is
 * persisted so the UI can list them and trigger AttachmentService later.
 *
 * <p>All methods are synchronous and blocking; the facade layer wraps them
 * in Task<T>.
 */
public class MailSyncService {

    private final MailSessionProvider mailSessionProvider;
    private final MailRepository mailRepository;
    private final AccountRepository accountRepository;

    public MailSyncService(MailSessionProvider mailSessionProvider,
                           MailRepository mailRepository,
                           AccountRepository accountRepository) {
        this.mailSessionProvider = mailSessionProvider;
        this.mailRepository = mailRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Fetches the most recent mails from the remote INBOX, persists any that
     * are not yet in the local cache (body encrypted at rest), and returns
     * the refreshed local inbox, newest first.
     */
    public List<Mail> syncInbox(Account account)
            throws MailFetchException, DatabaseException, CryptoException {

        Store store = mailSessionProvider.getImapStore(account);
        try {
            Folder inbox = store.getFolder("INBOX");
            if (!inbox.exists()) {
                throw new MailFetchException(ErrorCode.IMAP_FOLDER_NOT_FOUND,
                        "INBOX does not exist for this account");
            }
            inbox.open(Folder.READ_ONLY);
            try {
                Set<String> knownIds = knownServerMessageIds(account);
                Message[] messages = inbox.getMessages();
                int from = Math.max(0, messages.length - AppConstants.MAX_EMAILS_DISPLAYED);
                for (int i = messages.length - 1; i >= from; i--) {
                    String serverMessageId = messageIdOf(messages[i]);
                    if (serverMessageId == null || knownIds.contains(serverMessageId)) {
                        continue;
                    }
                    persistIncomingMail(account, messages[i], serverMessageId);
                }
            } finally {
                if (inbox.isOpen()) {
                    inbox.close(false);
                }
            }
            return mailRepository.getInbox(account, AppConstants.MAX_EMAILS_DISPLAYED);
        } catch (MailFetchException | DatabaseException | CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Failed to sync inbox", e);
        } finally {
            try {
                store.close();
            } catch (Exception ignored) {
                // Closing a failed store is best-effort.
            }
        }
    }

    /**
     * Marks a mail as read under one specific label. isRead lives on
     * Mail_Label, so the same mail can stay unread under other labels.
     * Server-side \Seen flag synchronization is out of MVP scope (TODO).
     */
    public void markAsRead(Mail mail, Label label) throws DatabaseException {
        mailRepository.markAsRead(mail.getIdMail(), label.getIdLabel(), true);
    }

    // ---------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------

    private Set<String> knownServerMessageIds(Account account)
            throws DatabaseException, CryptoException {
        Set<String> ids = new HashSet<>();
        for (Mail mail : mailRepository.getInbox(account, AppConstants.MAX_EMAILS_DISPLAYED)) {
            ids.add(mail.getServerMessageId());
        }
        return ids;
    }

    private String messageIdOf(Message message) throws MailFetchException {
        try {
            if (message instanceof MimeMessage mimeMessage) {
                return mimeMessage.getMessageID();
            }
            String[] header = message.getHeader("Message-ID");
            return header != null && header.length > 0 ? header[0] : null;
        } catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED,
                    "Failed to read the Message-ID header", e);
        }
    }

    private void persistIncomingMail(Account account, Message message, String serverMessageId)
            throws MailFetchException, DatabaseException, CryptoException {

        String senderEmail = MimeUtil.extractSender(message);
        if (senderEmail == null || senderEmail.isBlank()) {
            return; // id_sender_address is NOT NULL: a senderless mail cannot be stored.
        }
        EmailAddress sender = accountRepository.resolveOrCreateAddress(senderEmail, false);

        Mail mail = new Mail();
        mail.setIdAccount(account.getIdAccount());
        mail.setIdSenderAddress(sender.getIdAddress());
        mail.setServerMessageId(serverMessageId);
        mail.setSubject(MimeUtil.extractSubject(message));
        mail.setBodyPlainText(MimeUtil.extractPlainText(message));
        mail.setBodyHTML(null);
        mail.setDateReceived(receivedDate(message));

        // The account itself is the recipient of every synced mail.
        List<MailAddress> recipients = new ArrayList<>();
        MailAddress self = new MailAddress();
        self.setIdAddress(account.getIdAddress());
        self.setRecipientType("TO");
        recipients.add(self);

        List<Attachment> attachments = new ArrayList<>();
        collectAttachments(message, attachments);

        mailRepository.save(mail, recipients, attachments.isEmpty() ? null : attachments);
    }

    private LocalDate receivedDate(Message message) {
        try {
            Date sent = message.getSentDate();
            if (sent != null) {
                return sent.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception ignored) {
            // Fall through to now().
        }
        return LocalDate.now();
    }

    /**
     * Walks the MIME tree collecting attachment metadata (any part with a
     * file name). Binary content is deliberately left on the server until
     * the user explicitly downloads it via AttachmentService.
     */
    private void collectAttachments(Part part, List<Attachment> out) throws MailFetchException {
        try {
            if (part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    collectAttachments(multipart.getBodyPart(i), out);
                }
                return;
            }
            String fileName = part.getFileName();
            if (fileName != null && !fileName.isBlank()) {
                Attachment attachment = new Attachment();
                attachment.setFileName(fileName);
                attachment.setMimeType(part.getContentType() != null
                        ? part.getContentType() : "application/octet-stream");
                int size = part.getSize();
                attachment.setSizeBytes(size > 0 ? size : 0);
                attachment.setFilePath(null);
                out.add(attachment);
            }
        } catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED,
                    "Failed to inspect a MIME part for attachments", e);
        }
    }
}