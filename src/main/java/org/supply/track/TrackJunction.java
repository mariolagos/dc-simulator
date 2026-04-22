package org.supply.track;

import java.util.Objects;

/**
 * Explicit connection between two railway coordinates.
 */
public final class TrackJunction {

    private final RwyCoordinate from;
    private final RwyCoordinate to;

    public TrackJunction(RwyCoordinate from, RwyCoordinate to) {
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
    }

    public RwyCoordinate getFrom() {
        return from;
    }

    public RwyCoordinate getTo() {
        return to;
    }
}