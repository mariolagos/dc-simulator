package org.supply.track;

import java.util.List;
import java.util.Objects;

public final class RouteViewMapper {

    private static final double EPS = 1e-9;

    public RwyCoordinate toRailway(
            RouteView route,
            double pathPositionM
    ) {
        Objects.requireNonNull(route, "route");

        if (!Double.isFinite(pathPositionM)) {
            throw new IllegalArgumentException(
                    "pathPositionM must be finite: " + pathPositionM
            );
        }

        List<PathSample> samples = route.getSamples();

        PathSample first = samples.get(0);
        PathSample last = samples.get(samples.size() - 1);

        if (pathPositionM < first.getPathPositionM() - EPS
                || pathPositionM > last.getPathPositionM() + EPS) {
            throw new IllegalArgumentException(
                    "Path position outside route '"
                            + route.getRouteId()
                            + "': "
                            + pathPositionM
            );
        }

        for (PathSample sample : samples) {
            if (Math.abs(
                    pathPositionM - sample.getPathPositionM()
            ) <= EPS) {
                return sample.getRailwayCoordinate();
            }
        }

        for (int i = 0; i < samples.size() - 1; i++) {
            PathSample a = samples.get(i);
            PathSample b = samples.get(i + 1);

            if (pathPositionM > a.getPathPositionM()
                    && pathPositionM < b.getPathPositionM()) {
                return interpolate(a, b, pathPositionM);
            }
        }

        throw new IllegalStateException(
                "Could not map path position "
                        + pathPositionM
                        + " on route "
                        + route.getRouteId()
        );
    }

    private RwyCoordinate interpolate(
            PathSample a,
            PathSample b,
            double pathPositionM
    ) {
        RwyCoordinate from = a.getRailwayCoordinate();
        RwyCoordinate to = b.getRailwayCoordinate();

        boolean sameTrack =
                Objects.equals(
                        from.getSectionId(),
                        to.getSectionId()
                )
                        && Objects.equals(
                        from.getTrackId(),
                        to.getTrackId()
                );

        if (!sameTrack) {
            double transitionLengthM =
                    b.getPathPositionM()
                            - a.getPathPositionM();

            if (transitionLengthM > 1.0 + EPS) {
                throw new IllegalArgumentException(
                        "Section or track transition spans more than one metre"
                );
            }

            return from;
        }

        double fraction =
                (pathPositionM - a.getPathPositionM())
                        / (b.getPathPositionM()
                        - a.getPathPositionM());

        int railwayPositionM =
                (int) Math.round(
                        from.getPositionM()
                                + fraction
                                * (to.getPositionM()
                                - from.getPositionM())
                );

        return new RwyCoordinate(
                from.getSectionId(),
                RwyCoordinate.formatKmShort(railwayPositionM),
                railwayPositionM,
                from.getTrackId()
        );
    }
}