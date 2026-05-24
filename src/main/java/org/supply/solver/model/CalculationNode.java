package org.supply.solver.model;

public record CalculationNode(
        String id,
        String sourceId,
        String trackId,
        double positionM,
        CalculationNodeType type
) {
}