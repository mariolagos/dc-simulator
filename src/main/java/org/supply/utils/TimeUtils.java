package org.supply.utils;

public class TimeUtils {

    public static int parseHmsToSeconds(String hms) {
        String[] parts = hms.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid time format, expected HH:mm:ss: " + hms);
        }

        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        int s = Integer.parseInt(parts[2]);

        return h * 3600 + m * 60 + s;
    }

}
