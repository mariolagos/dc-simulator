package org.supply.track;

import java.util.List;
import java.util.Objects;

/**
 * A physical point that may carry one or more railway coordinates.
 *
 * This supports:
 * - segment boundaries
 * - section junctions
 * - forks / branches
 */
public final class TrackPoint {

    private final List<RwyCoordinate> railwayCoordinates;

    public TrackPoint(List<RwyCoordinate> railwayCoordinates) {
        this.railwayCoordinates = List.copyOf(
                Objects.requireNonNull(railwayCoordinates, "railwayCoordinates")
        );
        if (this.railwayCoordinates.isEmpty()) {
            throw new IllegalArgumentException("TrackPoint must contain at least one railway coordinate");
        }
    }

    public List<RwyCoordinate> getRailwayCoordinates() {
        return railwayCoordinates;
    }
}