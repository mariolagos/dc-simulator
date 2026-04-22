package org.supply.track;

import java.util.Objects;

/**
 * Linear segment within a route.
 */
public final class RouteSegment {

    private final int sequenceNo;
    private final RwyCoordinate startRwy;
    private final RwyCoordinate endRwy;
    private final int startModelM;
    private final int lengthM;

    public RouteSegment(int sequenceNo,
                        RwyCoordinate startRwy,
                        RwyCoordinate endRwy,
                        int startModelM,
                        int lengthM) {
        if (lengthM < 0) {
            throw new IllegalArgumentException("lengthM must be >= 0");
        }
        this.sequenceNo = sequenceNo;
        this.startRwy = Objects.requireNonNull(startRwy, "startRwy");
        this.endRwy = Objects.requireNonNull(endRwy, "endRwy");
        this.startModelM = startModelM;
        this.lengthM = lengthM;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public RwyCoordinate getStartRwy() {
        return startRwy;
    }

    public RwyCoordinate getEndRwy() {
        return endRwy;
    }

    public int getStartModelM() {
        return startModelM;
    }

    public int getLengthM() {
        return lengthM;
    }

    public int getEndModelM() {
        return startModelM + lengthM;
    }
}