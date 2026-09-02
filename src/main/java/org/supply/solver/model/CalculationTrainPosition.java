package org.supply.solver.model;

import org.supply.math.Real;

public record CalculationTrainPosition(
        String trainId,
        String sectionId,
        String trackId,
        String routeId,
        double positionM,
        Real pReqW
) {
}