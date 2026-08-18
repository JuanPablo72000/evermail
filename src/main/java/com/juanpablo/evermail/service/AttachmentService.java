package com.juanpablo.evermail.service;

import com.juanpablo.evermail.exception.AttachmentException;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Attachment;
import com.juanpablo.evermail.model.Mail;
import com.juanpablo.evermail.repository.MailRepository;
import com.juanpablo.evermail.util.FileUtil;
import com.juanpablo.evermail.util.SecurityUtil;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.InputStream;

/**
 * On-demand attachment download. The binary never lives in SQLite: this
 * service re-locates the raw MIME part on IMAP and hands its stream to
 * FileUtil, which encrypts it with the account's AES key and writes it to
 * the local filesystem in a single pass (no full in-memory copy, no
 * Base64 overhead). Once downloaded, the on-disk path is persisted on the
 * Attachment row so subsequent opens read the local encrypted file instead
 * of hitting the network.
 *
 * <p>All methods are synchronous and blocking; the facade layer wraps them
 * in Task<T>.
 */
public class AttachmentService {

    private final MailSessionProvider mailSessionProvider;
    private final MailRepository mailRepository;
    private final SecurityUtil securityUtil;

    public AttachmentService(MailSessionProvider mailSessionProvider,
                             MailRepository mailRepository,
                             SecurityUtil securityUtil) {
        this.mailSessionProvider = mailSessionProvider;
        this.mailRepository = mailRepository;
        this.securityUtil = securityUtil;
    }

    /**
     * Returns the local encrypted file for the attachment, downloading and
     * encrypting it on first use.
     */
    public File downloadAttachment(Attachment attachment, Account account)
            throws AttachmentException, DatabaseException, CryptoException {

        // 1. Already on disk? Skip the network entirely.
        if (attachment.getFilePath() != null) {
            File existing = new File(attachment.getFilePath());
            if (existing.exists()) {
                return existing;
            }
        }

        // 2. Parent mail gives us the server Message-ID needed to re-locate
        //    the message on IMAP.
        Mail mail = mailRepository.getById(attachment.getIdMail());
        if (mail == null) {
            throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND,
                    "Parent mail not found for attachment id " + attachment.getIdAttachment());
        }

        // 3. The account's AES key — FileUtil encrypts the stream with it.
        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(account.getIdAccount()));

        // 4. Open IMAP, locate the part, and stream it into FileUtil. The
        //    store/folder must stay open until FileUtil finishes reading.
        Store store;
        try {
            store = mailSessionProvider.getImapStore(account);
        } catch (Exception e) {
            throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND,
                    "Failed to open an IMAP session to download the attachment", e);
        }
        try {
            Folder inbox = store.getFolder("INBOX");
            if (!inbox.exists()) {
                throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND, "INBOX does not exist");
            }
            inbox.open(Folder.READ_ONLY);
            try {
                InputStream remoteStream = locatePartStream(inbox,
                        mail.getServerMessageId(), attachment.getFileName());

                File file = FileUtil.downloadAttachment(
                        String.valueOf(account.getIdAccount()),
                        attachment.getFileName(),
                        remoteStream,
                        key,
                        securityUtil);

                // 5. Persist the path so future calls are local-only.
                attachment.setFilePath(file.getAbsolutePath());
                mailRepository.updateAttachmentPath(attachment);
                return file;
            } finally {
                if (inbox.isOpen()) {
                    inbox.close(false);
                }
            }
        } catch (AttachmentException e) {
            throw e;
        } catch (Exception e) {
            throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND,
                    "Failed to download attachment '" + attachment.getFileName() + "'", e);
        } finally {
            try {
                store.close();
            } catch (Exception ignored) {
                // Closing a failed store is best-effort.
            }
        }
    }

    // ---------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------

    private InputStream locatePartStream(Folder inbox, String serverMessageId, String fileName)
            throws Exception {
        for (Message message : inbox.getMessages()) {
            if (serverMessageId.equals(messageIdOf(message))) {
                InputStream stream = findPartStream(message, fileName);
                if (stream != null) {
                    return stream;
                }
            }
        }
        throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND,
                "Attachment '" + fileName + "' no longer exists on the server");
    }

    private String messageIdOf(Message message) throws Exception {
        if (message instanceof MimeMessage mimeMessage) {
            return mimeMessage.getMessageID();
        }
        String[] header = message.getHeader("Message-ID");
        return header != null && header.length > 0 ? header[0] : null;
    }

    /**
     * Recursively walks the MIME tree until the part with this file name.
     * Uses isMimeType() (not getContent()) for the multipart check so leaf
     * parts are never decoded/consumed before their stream is handed over.
     */
    private InputStream findPartStream(Part part, String fileName) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    InputStream stream = findPartStream(multipart.getBodyPart(i), fileName);
                    if (stream != null) {
                        return stream;
                    }
                }
            }
            return null;
        }
        if (fileName.equals(part.getFileName())) {
            return part.getInputStream();
        }
        return null;
    }
}