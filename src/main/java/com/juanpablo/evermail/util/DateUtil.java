package com.juanpablo.evermail.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formats dates for display in the UI.
 * Stateless — all methods resolve their logic purely from the parameters
 * received.
 */
public final class DateUtil {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm");

    private DateUtil() {
        // Prevents instantiation — this class only holds static methods.
    }

    public static String formatForDisplay(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        return date.format(DISPLAY_FORMATTER);
    }
}