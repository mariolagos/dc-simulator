package org.dcsim.utils;

import java.util.Objects;

/**
 * Position parsing utilities.
 *
 * Supported formats:
 *  - "1 1+100"  -> line=1, km=1, m=100
 *  - "10.250"   -> (default line) km=10, m=250   (x.y means x km and y meters, no decimals)
 *  - "250"      -> meters on default line (interpreted as total meters)
 *
 * The legacy parse(String) keeps working and now accepts both "line km+m" and "km.m".
 */
public final class PositionUtils {

    private PositionUtils() {}

    /** Parse using default lineId=1 when not present in the string. */
    public static int[] parse(String s) {
        return parseFlexible(s, 1);
    }

    /**
     * Flexible parser that supports both "line km+m" and "km.m".
     * @param s position string
     * @param defaultLineId used when the string has no explicit line id (e.g., "km.m")
     * @return int[]{ lineId, km, meters }
     */
    public static int[] parseFlexible(String s, int defaultLineId) {
        Objects.requireNonNull(s, "position string");
        String t = s.trim();

        if (t.isEmpty()) {
            throw new IllegalArgumentException("Empty position string");
        }

        if (t.contains("+")) {
            // "line km+m" with a space between line and km+m, e.g. "1 1+100"
            String[] line_rest = t.split("\\s+", 2);
            if (line_rest.length != 2) {
                throw new IllegalArgumentException("Expected 'line km+meters' format: " + s);
            }
            int lineId = Integer.parseInt(line_rest[0]);
            String[] km_m = line_rest[1].split("\\+");
            if (km_m.length != 2) {
                throw new IllegalArgumentException("Expected 'km+meters' after line: " + s);
            }
            int km = Integer.parseInt(km_m[0]);
            int m  = Integer.parseInt(km_m[1]);
            return new int[]{ lineId, km, m };
        }

        if (t.contains(".")) {
            // "km.m" meaning x km and y meters (no decimals)
            String[] parts = t.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected 'km.m' (x km and y meters): " + s);
            }
            int km = Integer.parseInt(parts[0]);
            int m  = Integer.parseInt(parts[1]);
            return new int[]{ defaultLineId, km, m };
        }

        // Fallback: treat as absolute meters on default line
        int abs = Integer.parseInt(t);
        int km  = abs / 1000;
        int m   = abs % 1000;
        return new int[]{ defaultLineId, km, m };
    }

    /** Convert int[]{ lineId, km, m } to absolute meters along the line. */
    public static double toMeters(int[] pos) {
        if (pos == null || pos.length < 3) {
            throw new IllegalArgumentException("Position must be int[]{line, km, m}");
        }
        return pos[1] * 1000.0 + pos[2];
    }


    /** Pretty print [line, km, m] → "line km+m". */
    public static String format(int[] pos) {
        if (pos == null || pos.length < 3) return "?";
        return pos[0] + " " + pos[1] + "+" + String.format("%03d", pos[2]);
    }

}
