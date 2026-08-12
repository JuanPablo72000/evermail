package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.AttachmentDAO;
import com.juanpablo.evermail.dao.MailAddressDAO;
import com.juanpablo.evermail.dao.MailDAO;
import com.juanpablo.evermail.dao.MailLabelDAO;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Attachment;
import com.juanpablo.evermail.model.Mail;
import com.juanpablo.evermail.model.MailAddress;
import com.juanpablo.evermail.util.SecurityUtil;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Combines MailDAO, MailLabelDAO, MailAddressDAO, and AttachmentDAO into a
 * single domain-aggregate view of a Mail, and owns all encryption
 * orchestration around bodyPlainText/bodyHTML (see uml-dao.md, note 4).
 * <p>
 * Always uses {@code SecurityUtil.retrieveAesKey} — never generates a key —
 * since by the time any Mail is saved, its Account already exists and its
 * key was generated once in {@link AccountRepository#create}.
 */
public class MailRepository {

    private final MailDAO mailDAO;
    private final MailLabelDAO mailLabelDAO;
    private final MailAddressDAO mailAddressDAO;
    private final AttachmentDAO attachmentDAO;
    private final SecurityUtil securityUtil;

    public MailRepository(MailDAO mailDAO,
                          MailLabelDAO mailLabelDAO,
                          MailAddressDAO mailAddressDAO,
                          AttachmentDAO attachmentDAO,
                          SecurityUtil securityUtil) {
        this.mailDAO = mailDAO;
        this.mailLabelDAO = mailLabelDAO;
        this.mailAddressDAO = mailAddressDAO;
        this.attachmentDAO = attachmentDAO;
        this.securityUtil = securityUtil;
    }

    /**
     * Returns the mail with bodyPlainText/bodyHTML already decrypted, or null
     * if no mail exists with that id.
     */
    public Mail getById(int idMail) throws DatabaseException, CryptoException {
        Mail mail = mailDAO.findById(idMail);
        if (mail == null) {
            return null;
        }
        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(mail.getIdAccount()));
        return decryptBody(mail, key);
    }

    /**
     * Returns the most recent mails for an account (already decrypted),
     * newest first, capped at {@code limit}. The account's key is resolved
     * once and reused across every mail in the result, since they all belong
     * to the same account — a single keyring access instead of one per mail.
     */
    public List<Mail> getInbox(Account account, int limit) throws DatabaseException, CryptoException {
        List<Mail> mails = mailDAO.findByAccount(account.getIdAccount(), limit);
        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(account.getIdAccount()));

        List<Mail> decrypted = new ArrayList<>(mails.size());
        for (Mail mail : mails) {
            decrypted.add(decryptBody(mail, key));
        }
        return decrypted;
    }

    /**
     * Persists a complete incoming mail as a single business operation: the
     * Mail row itself (with bodyPlainText/bodyHTML encrypted), its recipients
     * (Mail_Address), and its attachments' metadata, all sharing one
     * already-resolved SecretKey (see uml-repositories.md, note 4).
     * <p>
     * {@code recipients} and {@code attachments} are not fields on the Mail
     * model (a Mail row alone has no place to carry them), so they travel as
     * separate parameters — mail.idMail is not required to be set beforehand,
     * it is populated on every entry after the insert.
     *
     * @param recipients may be empty but not null.
     * @param attachments may be empty or null (a mail with no attachments).
     */
    public Mail save(Mail mail, List<MailAddress> recipients, List<Attachment> attachments)
            throws DatabaseException, CryptoException {

        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(mail.getIdAccount()));

        String plainBodyText = mail.getBodyPlainText();
        String plainBodyHtml = mail.getBodyHTML();

        mail.setBodyPlainText(plainBodyText != null ? securityUtil.encrypt(plainBodyText, key) : null);
        mail.setBodyHTML(plainBodyHtml != null ? securityUtil.encrypt(plainBodyHtml, key) : null);

        int idMail = mailDAO.insert(mail);
        mail.setIdMail(idMail);

        mail.setBodyPlainText(plainBodyText);
        mail.setBodyHTML(plainBodyHtml);

        for (MailAddress recipient : recipients) {
            recipient.setIdMail(idMail);
        }
        mailAddressDAO.insertBatch(recipients);

        if (attachments != null) {
            for (Attachment attachment : attachments) {
                attachment.setIdMail(idMail);
                int idAttachment = attachmentDAO.insert(attachment);
                attachment.setIdAttachment(idAttachment);
            }
        }

        return mail;
    }

    /**
     * Marks a mail read/unread under a specific label. isRead lives on
     * Mail_Label (per the E-R model), not on Mail itself, so the same mail
     * can be read under one label and unread under another.
     */
    public void markAsRead(int idMail, int idLabel, boolean isRead) throws DatabaseException {
        mailLabelDAO.updateIsRead(idMail, idLabel, isRead);
    }

    /**
     * Deletes the mail. The schema cascades this to its Mail_Label,
     * Mail_Address, and Attachment rows (ON DELETE CASCADE), so no manual
     * orchestration is needed here.
     */
    public void delete(int idMail) throws DatabaseException {
        mailDAO.delete(idMail);
    }

    private Mail decryptBody(Mail mail, SecretKey key) throws CryptoException {
        if (mail.getBodyPlainText() != null) {
            mail.setBodyPlainText(securityUtil.decrypt(mail.getBodyPlainText(), key));
        }
        if (mail.getBodyHTML() != null) {
            mail.setBodyHTML(securityUtil.decrypt(mail.getBodyHTML(), key));
        }
        return mail;
    }
}