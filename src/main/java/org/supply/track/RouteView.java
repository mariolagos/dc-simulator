package org.supply.track;

import java.util.List;
import java.util.Objects;

/**
 * A named linearized view of the track.
 */
public final class RouteView {

    private final String routeId;
    private final String description;
    private final List<PathSample> samples;

    public RouteView(
            String routeId,
            String description,
            List<PathSample> samples
    ) {
        this.routeId = Objects.requireNonNull(routeId, "routeId");
        this.description = description;
        this.samples = List.copyOf(
                Objects.requireNonNull(samples, "samples")
        );

        if (this.samples.size() < 2) {
            throw new IllegalArgumentException(
                    "RouteView must contain at least two path samples"
            );
        }

        double previous = Double.NEGATIVE_INFINITY;

        for (PathSample sample : this.samples) {
            double positionM = sample.getPathPositionM();

            if (!Double.isFinite(positionM)) {
                throw new IllegalArgumentException(
                        "Route path position must be finite: " + positionM
                );
            }

            if (positionM <= previous) {
                throw new IllegalArgumentException(
                        "Route path positions must be strictly increasing"
                );
            }

            previous = positionM;
        }
    }

    public String getRouteId() {
        return routeId;
    }

    public String getDescription() {
        return description;
    }

    public List<PathSample> getSamples() {
        return samples;
    }
}