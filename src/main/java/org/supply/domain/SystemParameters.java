package org.supply.domain;

public record SystemParameters(
        double uNominalV,
        double uMin1V,
        double uMin2V,
        double uMaxV,
        double iTrainMaxA
) {
}