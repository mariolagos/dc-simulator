package org.supply.track;

/**
 * Service contract for coordinate transformations and route-based distance logic.
 */
public interface TrackTransformService {

    ModelCoordinate toModel(String routeId, RwyCoordinate railwayCoordinate);

    RwyCoordinate toRailway(String routeId, ModelCoordinate modelCoordinate);

    ModelCoordinate pathToModel(String routeId, double pathPositionM);

    RwyCoordinate pathToRailway(String routeId, double pathPositionM);

    /** Ordered local endpoints, including both coordinates at each section transition. */
    default java.util.List<ModelCoordinate> linePath(ModelCoordinate from, ModelCoordinate to) {
        if (!from.getSectionId().equals(to.getSectionId())) {
            throw new IllegalArgumentException("Track transform cannot resolve a cross-section line");
        }
        return java.util.List.of(from, to);
    }

    double distanceOnRoute(String routeId, RwyCoordinate from, RwyCoordinate to);
}