package org.supply.track;

public final class RwyCoordinateParser {

    private RwyCoordinateParser() {
    }

    public static RwyCoordinate parse(String text) {
        String[] parts = text.trim().split("\\s+");

        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException(
                    "Invalid railway coordinate, expected '<section> <km+m> [trackId]': " + text
            );
        }

        String sectionId = parts[0];
        String positionText = parts[1];
        String trackId = parts.length == 3 ? parts[2] : null;

        int positionM = parseKmPlusM(positionText);

        return new RwyCoordinate(sectionId, positionText, positionM, trackId);
    }

    private static int parseKmPlusM(String text) {
        String[] parts = text.split("\\+");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid railway position, expected km+m: " + text
            );
        }

        int km = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);

        return km * 1000 + m;
    }
}