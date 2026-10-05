package com.juanpablo.evermail.service;

import com.juanpablo.evermail.model.OAuthProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailProviderRequirementsTest {
    @Test void selectsPersonalMicrosoftSmtpFromVerifiedIssuerRatherThanEmailDomain() {
        var microsoft = OAuthProvider.MICROSOFT;
        String personal = "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0|subject";
        assertEquals("smtp-mail.outlook.com", microsoft.getSmtpHost(personal));
        assertEquals("smtp.office365.com", microsoft.getSmtpHost("https://login.microsoftonline.com/other/v2.0|subject"));
        assertEquals("smtp.office365.com", microsoft.getSmtpHost("legacy:person@outlook.com"));
        assertEquals("smtp.office365.com", microsoft.getSmtpHost(null));
        assertEquals("smtp.gmail.com", OAuthProvider.GOOGLE.getSmtpHost(personal));
    }

    @Test void mailTransportRequiresOAuthAndVerifiedEncryptedConnections() {
        var imap = MailSessionProvider.imapProperties(5000);
        assertEquals("XOAUTH2", imap.getProperty("mail.imaps.auth.mechanisms"));
        assertEquals("true", imap.getProperty("mail.imaps.ssl.enable"));
        assertEquals("true", imap.getProperty("mail.imaps.ssl.checkserveridentity"));
        var smtp = MailSessionProvider.smtpProperties(5000);
        assertEquals("XOAUTH2", smtp.getProperty("mail.smtp.auth.mechanisms"));
        assertEquals("true", smtp.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", smtp.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("false", smtp.getProperty("mail.debug.auth"));
        assertEquals("false", imap.getProperty("mail.debug.auth"));
    }
}
