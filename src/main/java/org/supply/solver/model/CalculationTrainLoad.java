package org.supply.solver.model;

import org.supply.math.Real;

public record CalculationTrainLoad(
        String trainId,
        String feedingNodeId,
        String returnNodeId,
        Real pReqW,
        Double iTrainMaxA
) {
    public CalculationTrainLoad {
        if (iTrainMaxA != null && (!Double.isFinite(iTrainMaxA) || iTrainMaxA <= 0))
            throw new IllegalArgumentException("i_train_max_A must be finite and > 0 for train " + trainId);
    }
    public CalculationTrainLoad(String trainId, String feedingNodeId, String returnNodeId, Real pReqW) {
        this(trainId, feedingNodeId, returnNodeId, pReqW, null);
    }
}