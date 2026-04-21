package org.supply.track;

import java.util.Objects;

/**
 * A named linearized view of the network.
 *
 * Used for:
 * - route-specific distance calculations
 * - diagrams
 * - path/model disambiguation
 */
public final class RouteView {

    private final String routeId;
    private final String description;

    public RouteView(String routeId, String description) {
        this.routeId = Objects.requireNonNull(routeId, "routeId");
        this.description = description;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getDescription() {
        return description;
    }
}