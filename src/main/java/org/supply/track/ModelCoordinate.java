package org.supply.track;

import java.util.Objects;

/**
 * Derived model coordinate used for simulation/export.
 */
public final class ModelCoordinate {

    private final String sectionId;
    private final int positionM;

    public ModelCoordinate(String sectionId, int positionM) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.positionM = positionM;
    }

    public String getSectionId() {
        return sectionId;
    }

    public int getPositionM() {
        return positionM;
    }

    @Override
    public String toString() {
        return "(" + sectionId + ", " + positionM + ")";
    }
}