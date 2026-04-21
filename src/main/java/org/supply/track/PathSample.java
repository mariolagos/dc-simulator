package org.supply.track;

import java.util.Objects;

/**
 * Mapping sample from a train-path position to a railway coordinate.
 */
public final class PathSample {

    private final double pathPositionM;
    private final RwyCoordinate railwayCoordinate;

    public PathSample(double pathPositionM, RwyCoordinate railwayCoordinate) {
        this.pathPositionM = pathPositionM;
        this.railwayCoordinate = Objects.requireNonNull(railwayCoordinate, "railwayCoordinate");
    }

    public double getPathPositionM() {
        return pathPositionM;
    }

    public RwyCoordinate getRailwayCoordinate() {
        return railwayCoordinate;
    }
}