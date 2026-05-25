package org.supply.solver.model;

public record CalculationNode(
        String id,
        String sourceId,
        String sectionId,
        String trackId,
        double positionM,
        CalculationNodeType type
) {
}