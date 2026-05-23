package org.supply.track;

import java.util.List;
import java.util.Objects;

/**
 * A named linearized view of the network.
 */
public final class RouteView {

    private final String routeId;
    private final String description;
    private final List<RouteSegment> segments;

    public RouteView(String routeId, String description, List<RouteSegment> segments) {
        this.routeId = Objects.requireNonNull(routeId, "routeId");
        this.description = description;
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (this.segments.isEmpty()) {
            throw new IllegalArgumentException("RouteView must contain at least one segment");
        }
    }

    public String getRouteId() {
        return routeId;
    }

    public String getDescription() {
        return description;
    }

    public List<RouteSegment> getSegments() {
        return segments;
    }
}