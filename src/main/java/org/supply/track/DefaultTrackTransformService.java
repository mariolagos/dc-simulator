package org.supply.track;

import java.util.Map;
import java.util.Objects;

/**
 * Minimal implementation of TrackTransformService based on the simplified track model.
 */
public final class DefaultTrackTransformService implements TrackTransformService {

    private final LoadedTrackModel trackModel;
    private final TrackSectionMapper mapper;
    private final RouteViewMapper routeViewMapper;

    public DefaultTrackTransformService(
            LoadedTrackModel trackModel
    ) {
        this.trackModel =
                Objects.requireNonNull(
                        trackModel,
                        "trackModel"
                );
        this.mapper = new TrackSectionMapper();
        this.routeViewMapper = new RouteViewMapper();
    }

    @Override
    public ModelCoordinate toModel(String routeId, RwyCoordinate railwayCoordinate) {
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(railwayCoordinate, "railwayCoordinate");

        // For MVP: routeId == sectionId
        String sectionId = railwayCoordinate.getSectionId();

        Map<String, TrackSection> sections = trackModel.getSectionsById();
        TrackSection section = sections.get(sectionId);

        if (section == null) {
            throw new IllegalArgumentException(
                    "No section found for coordinate: " + railwayCoordinate
            );
        }

        return mapper.toModel(section, railwayCoordinate);
    }

    @Override
    public RwyCoordinate toRailway(String routeId, ModelCoordinate modelCoordinate) {
        throw new UnsupportedOperationException("toRailway not implemented yet");
    }

    @Override
    public ModelCoordinate pathToModel(
            String routeId,
            double pathPositionM
    ) {
        RwyCoordinate railwayCoordinate =
                pathToRailway(
                        routeId,
                        pathPositionM
                );

        return toModel(
                routeId,
                railwayCoordinate
        );
    }

    @Override
    public RwyCoordinate pathToRailway(
            String routeId,
            double pathPositionM
    ) {
        RouteView route =
                trackModel
                        .getRouteViewsById()
                        .get(routeId);

        if (route == null) {
            throw new IllegalArgumentException(
                    "Unknown track route: " + routeId
            );
        }

        return routeViewMapper.toRailway(
                route,
                pathPositionM
        );
    }

    @Override
    public double distanceOnRoute(String routeId, RwyCoordinate from, RwyCoordinate to) {
        ModelCoordinate a = toModel(routeId, from);
        ModelCoordinate b = toModel(routeId, to);

        return Math.abs(a.getPositionM() - b.getPositionM());
    }
}