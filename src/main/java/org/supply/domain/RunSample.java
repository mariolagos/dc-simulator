package org.supply.domain;

public record RunSample(
        double timeS,
        String trainId,
        String sectionId,
        String trackId,
        String routeId,
        double positionM,
        double pReqW
) {}