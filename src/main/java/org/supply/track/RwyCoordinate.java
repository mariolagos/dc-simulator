package org.supply.track;

import java.util.Objects;

/**
 * Railway coordinate.
 *
 * Examples:
 * - "23 12+6 U"
 * - "1 0+100"
 */
public final class RwyCoordinate {

    private final String sectionId;
    private final String positionText;
    private final String trackId; // optional

    public RwyCoordinate(String sectionId, String positionText, String trackId) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.positionText = Objects.requireNonNull(positionText, "positionText");
        this.trackId = trackId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getPositionText() {
        return positionText;
    }

    public String getTrackId() {
        return trackId;
    }

    @Override
    public String toString() {
        return trackId == null || trackId.isBlank()
                ? sectionId + " " + positionText
                : sectionId + " " + positionText + " " + trackId;
    }
}