package com.juanpablo.evermail.util;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.MailFetchException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;

import java.io.IOException;

public final class MimeUtil {

    private MimeUtil() {
        // Prevents instantiation — this class only holds static methods.
    }

    public static String extractPlainText(Message mimeMessage) throws MailFetchException {
        try {
            Object content = mimeMessage.getContent();

            if (content instanceof String text) {
                return text;
            }

            if (content instanceof Multipart multipart) {
                StringBuilder plainText = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    Part part = multipart.getBodyPart(i);
                    if (part.isMimeType("text/plain")) {
                        plainText.append(part.getContent());
                    }
                }
                return plainText.toString();
            }

            return "";

        } catch (MessagingException | IOException e) {
            throw new MailFetchException(
                    ErrorCode.IMAP_FETCH_FAILED,
                    "Failed to extract plain text from message",
                    e
            );
        }
    }

    public static String extractSender(Message mimeMessage) throws MailFetchException {
        try {
            Address[] fromAddresses = mimeMessage.getFrom();
            if (fromAddresses == null || fromAddresses.length == 0) {
                return "";
            }
            return ((InternetAddress) fromAddresses[0]).getAddress();

        } catch (MessagingException e) {
            throw new MailFetchException(
                    ErrorCode.IMAP_FETCH_FAILED,
                    "Failed to extract sender from message",
                    e
            );
        }
    }

    public static String extractSubject(Message mimeMessage) throws MailFetchException {
        try {
            String subject = mimeMessage.getSubject();
            return subject != null ? subject : "";

        } catch (MessagingException e) {
            throw new MailFetchException(
                    ErrorCode.IMAP_FETCH_FAILED,
                    "Failed to extract subject from message",
                    e
            );
        }
    }

    public static String extractSenderName(Message mimeMessage) throws MailFetchException {
        try {
            Address[] fromAddresses = mimeMessage.getFrom();
            if (fromAddresses == null || fromAddresses.length == 0) {
                return "";
            }
            InternetAddress from = (InternetAddress) fromAddresses[0];
            String personal = from.getPersonal();
            return (personal != null && !personal.isBlank()) ? personal : from.getAddress();
        } catch (MessagingException e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED,
                    "Failed to extract sender name from message", e);
        }
    }
}