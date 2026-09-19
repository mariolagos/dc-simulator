package org.supply.io.export;

import org.supply.domain.RunSource;

/** Converts one physical run source to normalized SI samples. */
public interface RunDataReader {
    RunReadResult read(RunSource source) throws Exception;
}
