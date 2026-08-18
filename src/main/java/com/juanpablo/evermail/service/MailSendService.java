package com.juanpablo.evermail.service;

import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.MailSendException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Draft;
import com.juanpablo.evermail.model.EmailAddress;
import com.juanpablo.evermail.model.Mail;
import com.juanpablo.evermail.model.MailAddress;
import com.juanpablo.evermail.repository.AccountRepository;
import com.juanpablo.evermail.repository.MailRepository;
import com.juanpablo.evermail.util.EmailValidator;
import jakarta.mail.Message;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the entire "send a draft" business rule documented in the E-R model
 * (see uml-service.md, note 2): validate recipients, send via SMTP, and ONLY
 * if the send succeeds delete the Draft and record the sent Mail with its
 * recipients. Doing the SMTP call first guarantees the facade can never end
 * up with a deleted draft and no sent mail (or vice versa) on a partial
 * failure.
 *
 * <p>Recipients travel as raw email strings because neither Draft nor Mail
 * carry their recipient lists as fields — the compose screen submits exactly
 * what the user typed, and this service resolves each string to its
 * email_address row for persistence.
 *
 * <p>All methods are synchronous and blocking; the facade layer wraps them
 * in Task<T>.
 */
public class MailSendService {

    private final MailSessionProvider mailSessionProvider;
    private final MailRepository mailRepository;
    private final DraftService draftService;
    private final AccountRepository accountRepository;

    public MailSendService(MailSessionProvider mailSessionProvider,
                           MailRepository mailRepository,
                           DraftService draftService,
                           AccountRepository accountRepository) {
        this.mailSessionProvider = mailSessionProvider;
        this.mailRepository = mailRepository;
        this.draftService = draftService;
        this.accountRepository = accountRepository;
    }

    /**
     * Sends the draft to the given recipients via SMTP (XOAUTH2). On success
     * the draft is deleted and a Mail row is created so the sent message
     * appears in the account's history.
     *
     * @return the persisted sent Mail, with body in plain text.
     * @throws MailSendException if the SMTP exchange fails or there are no
     *         recipients; InvalidEmailAddressException (unchecked) if any
     *         address is malformed.
     */
    public Mail sendDraft(Draft draft, List<String> to, List<String> cc, List<String> bcc)
            throws MailSendException, DatabaseException, CryptoException {

        List<String> safeTo = to != null ? to : List.of();
        List<String> safeCc = cc != null ? cc : List.of();
        List<String> safeBcc = bcc != null ? bcc : List.of();

        // 1. Fail fast: validate every address before any network or DB work.
        if (safeTo.isEmpty() && safeCc.isEmpty() && safeBcc.isEmpty()) {
            throw new MailSendException(ErrorCode.SMTP_SEND_REJECTED,
                    "Cannot send a mail without at least one recipient");
        }
        for (String email : concat(safeTo, safeCc, safeBcc)) {
            EmailValidator.validateOrThrow(email); // throws InvalidEmailAddressException
        }

        // 2. Load the sending account (decrypted tokens) and its own address.
        Account account = accountRepository.getById(draft.getIdAccount());
        if (account == null) {
            throw new MailSendException(ErrorCode.SMTP_SEND_REJECTED,
                    "No account found for draft id " + draft.getIdDraft());
        }
        String senderEmail = accountRepository.getEmailOfAccount(account);

        // 3. Resolve recipients to email_address rows for persistence.
        List<MailAddress> recipients = new ArrayList<>();
        addRecipients(recipients, safeTo, "TO");
        addRecipients(recipients, safeCc, "CC");
        addRecipients(recipients, safeBcc, "BCC");

        // 4. Build and send the SMTP message. The draft is NOT touched yet:
        //    if this block throws, nothing is deleted nor recorded.
        Transport transport = mailSessionProvider.getSmtpTransport(account);
        String serverMessageId;
        try {
            MimeMessage message = new MimeMessage(mailSessionProvider.createSmtpSession());
            message.setFrom(new InternetAddress(senderEmail));
            setRecipients(message, Message.RecipientType.TO, safeTo);
            setRecipients(message, Message.RecipientType.CC, safeCc);
            setRecipients(message, Message.RecipientType.BCC, safeBcc);
            message.setSubject(draft.getSubject() != null ? draft.getSubject() : "");
            message.setText(draft.getBodyPlainText() != null ? draft.getBodyPlainText() : "", "utf-8");
            message.saveChanges();
            transport.sendMessage(message, message.getAllRecipients());
            serverMessageId = message.getMessageID() != null
                    ? message.getMessageID()
                    : "<" + System.currentTimeMillis() + "@evermail.local>";
        } catch (MailSendException e) {
            throw e;
        } catch (Exception e) {
            throw new MailSendException(ErrorCode.SMTP_SEND_REJECTED,
                    "SMTP send failed for draft id " + draft.getIdDraft(), e);
        } finally {
            try {
                transport.close();
            } catch (Exception ignored) {
                // Closing a failed transport is best-effort.
            }
        }

        // 5. Send succeeded → apply the business rule: delete draft, then
        //    record the sent mail with its recipients.
        draftService.delete(draft);

        Mail sentMail = new Mail();
        sentMail.setIdAccount(account.getIdAccount());
        sentMail.setIdSenderAddress(account.getIdAddress());
        sentMail.setServerMessageId(serverMessageId);
        sentMail.setSubject(draft.getSubject());
        sentMail.setBodyPlainText(draft.getBodyPlainText());
        sentMail.setBodyHTML(null);
        sentMail.setDateReceived(LocalDate.now());

        return mailRepository.save(sentMail, recipients, null);
    }

    private void addRecipients(List<MailAddress> target, List<String> emails, String type)
            throws DatabaseException {
        for (String email : emails) {
            EmailAddress address = accountRepository.resolveOrCreateAddress(email, false);
            MailAddress mailAddress = new MailAddress();
            mailAddress.setIdAddress(address.getIdAddress());
            mailAddress.setRecipientType(type);
            target.add(mailAddress);
        }
    }

    private void setRecipients(MimeMessage message, Message.RecipientType type, List<String> emails)
            throws MailSendException {
        if (emails.isEmpty()) {
            return;
        }
        try {
            message.setRecipients(type, InternetAddress.parse(String.join(",", emails)));
        } catch (Exception e) {
            throw new MailSendException(ErrorCode.SMTP_SEND_REJECTED,
                    "Failed to build recipient list of type " + type, e);
        }
    }

    private List<String> concat(List<String> a, List<String> b, List<String> c) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        all.addAll(c);
        return all;
    }
}