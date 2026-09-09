package org.supply.track;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of loading the simplified track section from configuration.
 */
public final class LoadedTrackModel {

    private final Map<String, TrackSection> sectionsById;
    private final List<TrackJunction> junctions;
    private final List<Station> stations;
    private final Map<String, RouteView> routeViewsById;

    public LoadedTrackModel(
            Map<String, TrackSection> sectionsById,
            List<TrackJunction> junctions,
            List<Station> stations
    ) {
        this(
                sectionsById,
                junctions,
                stations,
                Map.of()
        );
    }

    public LoadedTrackModel(
            Map<String, TrackSection> sectionsById,
            List<TrackJunction> junctions,
            List<Station> stations,
            Map<String, RouteView> routeViewsById
    ) {
        this.sectionsById =
                Map.copyOf(
                        Objects.requireNonNull(
                                sectionsById,
                                "sectionsById"
                        )
                );

        this.junctions =
                List.copyOf(
                        Objects.requireNonNull(
                                junctions,
                                "junctions"
                        )
                );

        this.stations =
                List.copyOf(
                        Objects.requireNonNull(
                                stations,
                                "stations"
                        )
                );

        this.routeViewsById =
                Map.copyOf(
                        Objects.requireNonNull(
                                routeViewsById,
                                "routeViewsById"
                        )
                );
    }


    public Map<String, TrackSection> getSectionsById() {
        return sectionsById;
    }

    public List<TrackJunction> getJunctions() {
        return junctions;
    }

    public List<Station> getStations() {
        return stations;
    }

    public Map<String, RouteView> getRouteViewsById() {
        return routeViewsById;
    }
}