package org.supply.io.export;

/**
 * A source-independent train-run sample in SI units.
 * Optional measured quantities are represented by {@code null}.
 */
public record RunSample(
        double timeS,
        double positionM,
        double powerW,
        Double speedMps,
        Double voltageV,
        Double currentA
) {
}
