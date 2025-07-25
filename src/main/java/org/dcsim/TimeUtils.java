package org.dcsim;

public class TimeUtils {
    public static String format(int secondsSinceMidnight) {
        int h = secondsSinceMidnight / 3600;
        int m = (secondsSinceMidnight % 3600) / 60;
        int s = secondsSinceMidnight % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
