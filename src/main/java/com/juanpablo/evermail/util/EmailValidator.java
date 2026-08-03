package com.juanpablo.evermail.util;

import com.juanpablo.evermail.exception.InvalidEmailAddressException;

import java.util.regex.Pattern;

/**
 * Validates email address format using a standard regular expression.
 * Stateless — all methods resolve their logic purely from the parameters
 * received.
 */
public final class EmailValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private EmailValidator() {
        // Prevents instantiation — this class only holds static methods.
    }

    public static boolean isValidFormat(String email) {
        if (email == null) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    public static void validateOrThrow(String email) throws InvalidEmailAddressException {
        if (!isValidFormat(email)) {
            throw new InvalidEmailAddressException(
                    "email",
                    "Invalid email address format: " + email
            );
        }
    }
}