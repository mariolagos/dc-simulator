package org.dcsim.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for parsing and formatting track positions.
 * Canonical form: "line km+meters" (e.g. "1 12+345").
 */
public final class PositionUtils {

    private static final Pattern STRICT = Pattern.compile("(\\d+)\\s+(\\d+)\\+(\\d+)");
    // tolerant: "1 12+345", "12+345", "1 12.345", "12.345km", "1 12.345 km"
    private static final Pattern FLEXIBLE = Pattern.compile(
            "(?:(\\d+)\\s+)?" +            // optional line
                    "(\\d+)(?:[.,](\\d+))?" +      // km (maybe decimal)
                    "(?:\\+?(\\d+))?" +            // optional +meters
                    "(?:\\s*km)?",
            Pattern.CASE_INSENSITIVE);

    private PositionUtils() {}

    /** Strict parse: "line km+meters" → [line, km, m]. */
    public static int[] parse(String pos) {
        Matcher m = STRICT.matcher(pos.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid position string: " + pos);
        }
        int line = Integer.parseInt(m.group(1));
        int km   = Integer.parseInt(m.group(2));
        int mm   = Integer.parseInt(m.group(3));
        return new int[] { line, km, mm };
    }

    /** Tolerant parse. Missing lineId ⇒ default 1. */
    public static int[] parseFlexible(String pos) {
        Matcher m = FLEXIBLE.matcher(pos.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid flexible position string: " + pos);
        }
        int line = (m.group(1) != null) ? Integer.parseInt(m.group(1)) : 1;
        int km   = Integer.parseInt(m.group(2));
        int mm   = 0;

        if (m.group(4) != null) {
            // explicit +meters
            mm = Integer.parseInt(m.group(4));
        } else if (m.group(3) != null) {
            // decimal part → meters (truncate/pad to 3 digits)
            String dec = m.group(3);
            if (dec.length() > 3) dec = dec.substring(0, 3);
            while (dec.length() < 3) dec += "0";
            mm = Integer.parseInt(dec);
        }
        return new int[] { line, km, mm };
    }

    /** Convert [line, km, m] → meters along line. */
    public static double toMeters(int[] pos) {
        return pos[1] * 1000.0 + pos[2];
    }

    /** Format as "line km+mmm". */
    public static String format(int lineId, double meters) {
        int km = (int) (meters / 1000);
        int mm = (int) Math.round(meters - km * 1000.0);
        if (mm == 1000) { km += 1; mm = 0; }
        return String.format("%d %d+%03d", lineId, km, mm);
    }

    /** Convenience: assume line 1 if not known. */
    public static String format(double meters) {
        return format(1, meters);
    }

    /** Normalize any input string to canonical "line km+mmm". */
    public static String normalize(String pos) {
        int[] p = parseFlexible(pos);
        return String.format("%d %d+%03d", p[0], p[1], p[2]);
    }
}
