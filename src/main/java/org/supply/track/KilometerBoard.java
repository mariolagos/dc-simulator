package org.supply.track;

import java.util.Objects;

/**
 * Input row from track.kilometer_boards.
 *
 * Order is significant and must be preserved.
 */
public final class KilometerBoard {

    private final String sectionId;
    private final String kmText;
    private final int kmPositionM;
    private final int lengthToNextM;

    public KilometerBoard(String sectionId, String kmText, int kmPositionM, int lengthToNextM) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.kmText = Objects.requireNonNull(kmText, "kmText");
        if (lengthToNextM < 0) {
            throw new IllegalArgumentException("lengthToNextM must be >= 0");
        }
        this.kmPositionM = kmPositionM;
        this.lengthToNextM = lengthToNextM;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getKmText() {
        return kmText;
    }

    public int getKmPositionM() {
        return kmPositionM;
    }

    public int getLengthToNextM() {
        return lengthToNextM;
    }

    public RwyCoordinate toRwyCoordinate() {
        return new RwyCoordinate(sectionId, kmText, kmPositionM, null);
    }
}