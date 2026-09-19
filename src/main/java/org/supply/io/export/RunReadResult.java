package org.supply.io.export;

import java.util.List;

/** Normalized samples and optional source-defined relative departure time. */
public record RunReadResult(
        List<RunSample> samples,
        Double relativeDepartureS
) {
    public RunReadResult {
        samples = List.copyOf(samples);
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("A run source contains no samples");
        }
    }
}
