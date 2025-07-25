package org.dcsim;

public class PositionUtils {
    public static String format(double meters) {
        int km = (int) (meters / 1000);
        int m = (int) (meters % 1000);
        return String.format("1 %d+ %d", km, m);
    }
}
