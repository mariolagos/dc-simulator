package org.supply.solver.model;

import org.supply.math.Real;

public record CalculationTrainPosition(
        String trainId,
        String sectionId,
        String trackId,
        String routeId,
        double routePositionM,
        double positionM,
        Real pReqW,
        Double speedMps
) {
    public CalculationTrainPosition(
            String trainId, String sectionId, String trackId, String routeId,
            double routePositionM, double positionM, Real pReqW
    ) {
        this(trainId, sectionId, trackId, routeId,
                routePositionM, positionM, pReqW, null);
    }
}
