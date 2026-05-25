package org.supply.solver.model;

import org.supply.math.Real;

public record CalculationTrainLoad(
        String trainId,
        String feedingNodeId,
        String returnNodeId,
        Real pReqW
) {
}