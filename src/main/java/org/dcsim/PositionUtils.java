package org.dcsim;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PositionUtils {

    private static final Pattern fullPattern = Pattern.compile("(\\d+)\\s*(\\d+)\\s*\\+\\s*(\\d+)");
    private static final Pattern shortPattern = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)");

    public static double[] parseKmPlus(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Position string is empty");
        }

        Matcher m1 = fullPattern.matcher(s);
        if (m1.matches()) {
            int section = Integer.parseInt(m1.group(1));
            int km = Integer.parseInt(m1.group(2));
            int m = Integer.parseInt(m1.group(3));
            return new double[] { section, km * 1000.0 + m };
        }

        Matcher m2 = shortPattern.matcher(s);
        if (m2.matches()) {
            int km = Integer.parseInt(m2.group(1));
            int m = Integer.parseInt(m2.group(2));
            return new double[] { 1.0, km * 1000.0 + m };
        }

        throw new IllegalArgumentException("Invalid km+ format: '" + s + "'");
    }

    public static String format(double meters) {
        int km = (int) (meters / 1000);
        int m = (int) (meters % 1000);
        return String.format("1 %d+ %d", km, m);
    }
}
