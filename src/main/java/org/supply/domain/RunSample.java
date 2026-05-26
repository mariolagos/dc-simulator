package org.supply.domain;

public record RunSample(
        double timeS,
        String trainId,
        String sectionId,
        String trackId,
        double positionM,
        double pReqW
) {
}