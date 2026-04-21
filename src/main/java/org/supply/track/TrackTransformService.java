package org.supply.track;

/**
 * Service contract for coordinate transformations and route-based distance logic.
 *
 * No implementation is defined at this stage.
 */
public interface TrackTransformService {

    /**
     * Convert a railway coordinate to a model coordinate.
     */
    ModelCoordinate toModel(RwyCoordinate railwayCoordinate);

    /**
     * Convert a model coordinate back to a railway coordinate for a given route/view.
     */
    RwyCoordinate toRailway(String routeId, ModelCoordinate modelCoordinate);

    /**
     * Convert a train-path position to a model coordinate for a given route/view.
     */
    ModelCoordinate pathToModel(String routeId, double pathPositionM);

    /**
     * Convert a train-path position to a railway coordinate for a given route/view.
     */
    RwyCoordinate pathToRailway(String routeId, double pathPositionM);

    /**
     * Compute route distance between two railway coordinates.
     */
    double distanceOnRoute(String routeId, RwyCoordinate from, RwyCoordinate to);
}