// org/dcsim/util/PositionUtils.java
package org.dcsim.utils;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to parse positions. Supports:
 * - "line km+meters"  e.g. "1 0+170"
 * - "km.m"            e.g. "1.170"   (x is km, y is meters)
 * - plain meters      e.g. "1170"    (interpreted as meters on line=1 by default)
 */
public final class PositionUtils {

    private static final Pattern LINE_KM_M =
            Pattern.compile("^\\s*(\\d+)\\s+(\\d+)\\+(\\d+)\\s*$"); // "1 0+170"

    private PositionUtils() {
    }

    public static class ParsedPosition {
        public final int lineId;
        public final double meters;

        public ParsedPosition(int lineId, double meters) {
            this.lineId = lineId;
            this.meters = meters;
        }
    }

    /** Flexible parse: "1 0+170", "1.170", "1170" */
    public static ParsedPosition parseFlexible(String text) {
        if (text == null) throw new IllegalArgumentException("position text is null");
        String s = text.trim();

        // Case A: "line km+meters"
        Matcher m = LINE_KM_M.matcher(s);
        if (m.matches()) {
            int line = Integer.parseInt(m.group(1));
            int km   = Integer.parseInt(m.group(2));
            int met  = Integer.parseInt(m.group(3));
            return new ParsedPosition(line, km * 1000.0 + met);
        }

        // Case B: "km.m"  (x is km, y is meters)
        // Example: "1.170" -> 1 km + 170 m => 1170 m
        if (s.contains(".")) {
            String[] parts = s.split("\\.");
            if (parts.length == 2 && isInteger(parts[0]) && isInteger(parts[1])) {
                int km  = Integer.parseInt(parts[0]);
                int met = Integer.parseInt(parts[1]);
                return new ParsedPosition(1, km * 1000.0 + met); // default line=1
            }
        }

        // Case C: plain meters (integer)
        if (isInteger(s)) {
            int meters = Integer.parseInt(s);
            return new ParsedPosition(1, meters); // default line=1
        }

        throw new IllegalArgumentException("Unrecognized position format: '" + text + "'");
    }

    private static boolean isInteger(String t) {
        if (t == null || t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (i == 0 && (c == '+' || c == '-')) continue;
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * Parse "1 7+770" or "7+770" or "1 0+0" into [line, km, meters].
     */
    @Deprecated
    public static int[] parse(String position) {

        if (position == null) throw new IllegalArgumentException("null position");
        String s = position.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty position");

        String lineToken;
        String kmPlus;
        String[] parts = s.split("\\s+");
        if (parts.length == 1) {
            // No explicit line → default to 1
            lineToken = "1";
            kmPlus = parts[0];
        } else {
            lineToken = parts[0];
            kmPlus = parts[1];
        }

        String[] km_m = kmPlus.split("\\+");
        if (km_m.length != 2) {
            throw new IllegalArgumentException("Invalid km+meters: " + position);
        }

        int line = Integer.parseInt(lineToken);
        int km = Integer.parseInt(km_m[0]);
        int m = Integer.parseInt(km_m[1]);
        return new int[]{line, km, m};
    }

    /**
     * Convert a parsed position [line, km, meters] to meters within that line.
     */
    public static double toMeters(int[] pos) {
        if (pos == null || pos.length != 3) throw new IllegalArgumentException("pos must be [line, km, meters]");
        return pos[1] * 1000.0 + pos[2];
    }

    /**
     * True if both positions are on the same line.
     */
    public static boolean sameLine(int[] a, int[] b) {
        return a[0] == b[0];
    }

    /**
     * Signed difference in meters within the same line: b - a.
     */
    public static double deltaMeters(int[] a, int[] b) {
        if (!sameLine(a, b)) {
            throw new IllegalArgumentException("Positions belong to different lines");
        }
        return toMeters(b) - toMeters(a);
    }

    /**
     * Absolute distance in meters within the same line.
     */
    public static double distanceMeters(int[] a, int[] b) {
        return Math.abs(deltaMeters(a, b));
    }


    public static String format(double meters) {
        int km = (int) (meters / 1000);
        int m = (int) (meters % 1000);
        return String.format("1 %d+ %d", km, m);
    }

}
