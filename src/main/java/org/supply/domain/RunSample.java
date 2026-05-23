package org.supply.domain;

public record RunSample(
        double timeS,
        String trainId,
        String trackId,
        double positionM,
        double pReqW
) {
}