package org.supply.domain;

public record SystemParameters(
        double uNominalV,
        double uMinV,
        double uCutoffV,
        double uMaxV,
        double iTrainMaxA
) {
}