package com.juanpablo.evermail.util;

import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.exception.ValidationException;
import com.juanpablo.evermail.model.Recipient;
import jakarta.mail.internet.InternetAddress;
import java.net.IDN;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class EmailValidator {
    private EmailValidator() {
    }

    public static String normalize(String input) throws ValidationException {
        try {
            if (input == null || input.contains("\r") || input.contains("\n")) {
                throw new IllegalArgumentException();
            }
            String value = input.strip();
            int at = value.lastIndexOf('@');
            if (at < 1 || at == value.length() - 1) {
                throw new IllegalArgumentException();
            }
            String normalized = value.substring(0, at) + "@"
                    + IDN.toASCII(value.substring(at + 1), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            InternetAddress address = new InternetAddress(normalized, true);
            address.validate();
            if (!normalized.equals(address.getAddress()) || address.getPersonal() != null) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (Exception e) {
            throw new ValidationException(ErrorCode.INVALID_RECIPIENT, "Invalid email address");
        }
    }

    public static List<Recipient> validateRecipients(List<Recipient> recipients) throws ValidationException {
        if (recipients == null || recipients.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_RECIPIENT, "At least one recipient is required");
        }
        var unique = new LinkedHashMap<String, Recipient>();
        for (Recipient recipient : recipients) {
            if (recipient == null || recipient.getType() == null) {
                throw new ValidationException(ErrorCode.INVALID_RECIPIENT, "Missing recipient type");
            }
            String email = normalize(recipient.getEmail());
            Recipient previous = unique.get(email);
            if (previous != null && previous.getType() != recipient.getType()) {
                throw new ValidationException(ErrorCode.INVALID_RECIPIENT, "A recipient cannot have conflicting roles");
            }
            unique.putIfAbsent(email, new Recipient(email, email, recipient.getDisplayName(), recipient.getType()));
        }
        return List.copyOf(unique.values());
    }
}
