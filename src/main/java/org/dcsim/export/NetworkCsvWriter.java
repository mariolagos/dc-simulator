package org.dcsim.export;

import org.dcsim.electric.GridModel;

import java.io.IOException;
import java.nio.file.Path;

public final class NetworkCsvWriter {
    public void writeNetworkCsvs(GridModel model, Path outDir) throws IOException {
        // writes sections.csv, track.csv, nodes.csv, substations.csv, lines.csv
    }
}
