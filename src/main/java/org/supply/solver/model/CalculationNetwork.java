package org.supply.solver.model;

import java.util.List;

public record CalculationNetwork(
        List<CalculationNode> nodes,
        List<CalculationBranch> branches,
        List<CalculationTrainLoad> trainLoads,
        List<ElectricalElement> elements
) {
}