package org.supply.solver.model;

import org.supply.math.Real;

public record CalculationBranch(
        String id,
        String sourceId,
        String fromNodeId,
        String toNodeId,
        Real resistanceOhm
) {
}