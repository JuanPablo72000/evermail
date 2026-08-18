package com.juanpablo.evermail.service;

import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.MailFetchException;
import com.juanpablo.evermail.exception.MailSendException;
import com.juanpablo.evermail.exception.OAuthAuthenticationException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.EmailAddress;
import com.juanpablo.evermail.model.OAuthProvider;
import com.juanpablo.evermail.repository.AccountRepository;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;

import java.util.Properties;

/**
 * Centralizes the creation of all authenticated Jakarta Mail sessions
 * (IMAP Store and SMTP Transport) using XOAUTH2. Before opening any
 * connection it delegates to AuthService.refreshTokenIfNeeded() so no
 * other class in the app needs to think about token expiration.
 *
 * <p>All methods are synchronous and blocking; the facade layer wraps them
 * in Task<T> so network I/O never freezes the JavaFX Application Thread.
 */
public class MailSessionProvider {

    private final AuthService authService;
    private final AccountRepository accountRepository;

    public MailSessionProvider(AuthService authService, AccountRepository accountRepository) {
        this.authService = authService;
        this.accountRepository = accountRepository;
    }

    /**
     * Opens an authenticated IMAP Store for the given account using XOAUTH2.
     * The returned Store is connected and ready to open folders; the caller
     * is responsible for closing it.
     */
    public Store getImapStore(Account account) throws MailFetchException {
        refreshToken(account);
        String email = resolveEmail(account);
        OAuthProvider provider = account.getProvider();

        Properties props = new Properties();
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.auth.mechanisms", "XOAUTH2");

        Session session = Session.getInstance(props);
        try {
            Store store = session.getStore("imaps");
            store.connect(provider.getImapHost(), provider.getImapPort(),
                    email, account.getAccessToken());
            return store;
        } catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_CONNECTION_FAILED,
                    "Failed to open IMAP session for " + email, e);
        }
    }

    /**
     * Opens an authenticated SMTP Transport for the given account using
     * XOAUTH2. The returned Transport is connected and ready to send; the
     * caller is responsible for closing it.
     */
    public Transport getSmtpTransport(Account account) throws MailSendException {
        try {
            refreshToken(account);
        } catch (MailFetchException e) {
            throw new MailSendException(ErrorCode.SMTP_CONNECTION_FAILED,
                    "Failed to refresh token before opening SMTP session", e);
        }
        String email;
        try {
            email = resolveEmail(account);
        } catch (MailFetchException e) {
            throw new MailSendException(ErrorCode.SMTP_CONNECTION_FAILED,
                    "Failed to resolve account email before opening SMTP session", e);
        }
        OAuthProvider provider = account.getProvider();

        Session session = createSmtpSession();

        try {
            Transport transport = session.getTransport("smtp");
            transport.connect(provider.getSmtpHost(), provider.getSmtpPort(),
                    email, account.getAccessToken());
            return transport;
        } catch (Exception e) {
            throw new MailSendException(ErrorCode.SMTP_CONNECTION_FAILED,
                    "Failed to open SMTP session for " + email, e);
        }
    }

    private void refreshToken(Account account) throws MailFetchException {
        try {
            authService.refreshTokenIfNeeded(account);
        } catch (OAuthAuthenticationException | DatabaseException | CryptoException e) {
            throw new MailFetchException(ErrorCode.IMAP_CONNECTION_FAILED,
                    "Failed to refresh OAuth token before opening a mail session", e);
        }
    }

    private String resolveEmail(Account account) throws MailFetchException {
        try {
            return accountRepository.getEmailOfAccount(account);
        } catch (DatabaseException e) {
            throw new MailFetchException(ErrorCode.IMAP_CONNECTION_FAILED,
                    "Failed to resolve the account's email address", e);
        }
    }

    /**
     * Builds the Jakarta Mail Session configured for SMTP over XOAUTH2 with
     * STARTTLS. Exposed so MailSendService can construct its MimeMessage
     * against the same configuration the Transport uses — session creation
     * stays centralized here instead of being duplicated in every service
     * that sends mail.
     */
    public Session createSmtpSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.starttls.enable", "true");
        return Session.getInstance(props);
    }
}