package com.juanpablo.evermail.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateUtil {
    private DateUtil() {
    }

    public static String format(Instant instant, ZoneId zone) {
        return instant == null ? "" : DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm")
                .withZone(zone).format(instant);
    }
}
