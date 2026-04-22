package org.supply.track;

import java.util.Objects;

/**
 * Railway coordinate.
 */
public final class RwyCoordinate {

    private final String sectionId;
    private final String positionText;
    private final int positionM;
    private final String trackId; // optional

    public RwyCoordinate(String sectionId, String positionText, int positionM, String trackId) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.positionText = Objects.requireNonNull(positionText, "positionText");
        this.positionM = positionM;
        this.trackId = trackId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getPositionText() {
        return positionText;
    }

    public int getPositionM() {
        return positionM;
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