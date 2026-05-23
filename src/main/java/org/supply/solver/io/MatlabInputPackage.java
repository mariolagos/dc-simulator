package org.supply.solver.io;

import org.supply.domain.*;

import java.util.List;

public record MatlabInputPackage(
        List<Node> nodes,
        List<Line> lines,
        List<PowerInstallation> powerInstallations,
        List<InstallationConnection> installationConnections
//        SystemParameters systemParameters,
//        List<RunSample> runSamples
) {
}