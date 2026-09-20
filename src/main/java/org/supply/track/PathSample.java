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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PathSample other)) {
            return false;
        }

        return Double.compare(pathPositionM, other.pathPositionM) == 0
                && Objects.equals(railwayCoordinate, other.railwayCoordinate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathPositionM, railwayCoordinate);
    }
}