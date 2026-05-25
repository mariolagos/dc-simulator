package org.supply.solver.model;

public record CalculationTrainPosition(
        String trainId,
        String sectionId,
        String trackId,
        double positionM
) {
}