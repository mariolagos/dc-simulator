package org.dcsim.utils;

import java.time.*;

public class TimeUtils {

    public static String format(int secondsSinceMidnight) {
        int h = secondsSinceMidnight / 3600;
        int m = (secondsSinceMidnight % 3600) / 60;
        int s = secondsSinceMidnight % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** Parse a variety of inputs into an Instant.
     *  Accepts:
     *   - "2025-08-11T08:00:00+02:00" (ISO with offset)
     *   - "2025-08-11T08:00:00"       (local datetime → system zone)
     *   - "08:00:00"                  (time-only → today, system zone)
     */
    public static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty simulation time string");
        }
        try { return OffsetDateTime.parse(text).toInstant(); } catch (Exception ignore) {}
        try {
            LocalDateTime ldt = LocalDateTime.parse(text);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignore) {}
        try {
            LocalTime lt = LocalTime.parse(text);
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            return lt.atDate(today).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignore) {}
        throw new IllegalArgumentException("Unsupported time format: " + text);
    }
}
