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
        PathSample previousSample = null;

        for (PathSample sample : this.samples) {
            double positionM = sample.getPathPositionM();

            if (!Double.isFinite(positionM)) {
                throw new IllegalArgumentException(
                        "Route path position must be finite: " + positionM
                );
            }

            if (previousSample != null) {
                double previousPositionM = previousSample.getPathPositionM();
                int positionComparison =
                        Double.compare(positionM, previousPositionM);

                if (positionComparison < 0) {
                    throw new IllegalArgumentException(
                            "Route path positions must be non-decreasing"
                    );
                }

                if (positionComparison == 0
                        && !sample.equals(previousSample)) {
                    throw new IllegalArgumentException(
                            "Samples at the same route path position must be identical: "
                                    + positionM
                    );
                }
            }

            previousSample = sample;
        }    }

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