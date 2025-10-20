package org.dcsim.testing;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridResult;
import org.dcsim.math.Real;
import java.nio.file.Path;

/** Opt-in wrappers for GraphExport, enabled by -Ddcsim.graph (anything but "off"). */
public final class GraphExportOpt {
    private GraphExportOpt() {}

    public static boolean isEnabled() {
        return !"off".equalsIgnoreCase(System.getProperty("dcsim.graph", "off"));
    }

    public static void writeTopologyIfEnabled(GridModel<Real> m, Path outDot, String title) {
        if (!isEnabled()) return;
        try { GraphExport.writeDotTopology(m, outDot, title); } catch (Exception ignored) {}
    }

    public static void writeWithResultsIfEnabled(GridModel<Real> m, GridResult res, Path outDot, String title) {
        if (!isEnabled()) return;
        try { GraphExport.writeDotWithResults(m, res, outDot, title); } catch (Exception ignored) {}
    }
}
