package org.supply.track;

/**
 * Service contract for coordinate transformations and route-based distance logic.
 */
public interface TrackTransformService {

    ModelCoordinate toModel(String routeId, RwyCoordinate railwayCoordinate);

    RwyCoordinate toRailway(String routeId, ModelCoordinate modelCoordinate);

    ModelCoordinate pathToModel(String routeId, double pathPositionM);

    RwyCoordinate pathToRailway(String routeId, double pathPositionM);

    double distanceOnRoute(String routeId, RwyCoordinate from, RwyCoordinate to);
}