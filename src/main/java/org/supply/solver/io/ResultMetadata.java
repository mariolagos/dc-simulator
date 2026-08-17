package org.supply.solver.io;

public record ResultMetadata(
        String project,
        String scenario,
        String baseHash,
        String generatedAt
) {}
