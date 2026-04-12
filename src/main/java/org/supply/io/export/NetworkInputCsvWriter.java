package org.supply.io.export;

import org.supply.model.GridModel;

import java.io.IOException;
import java.nio.file.Path;

public final class NetworkInputCsvWriter {

    public void writeAll(GridModel model, Path exportDir) throws IOException {
        writeNodes(model, exportDir.resolve("nodes.csv"));
        writeLines(model, exportDir.resolve("lines.csv"));
        writePowerInstallations(model, exportDir.resolve("powerInstallations.csv"));
        writeInstallationConnections(model, exportDir.resolve("installationConnections.csv"));
    }

    public void writeNodes(GridModel model, Path file) throws IOException {
    }

    public void writeLines(GridModel model, Path file) throws IOException {
    }

    public void writePowerInstallations(GridModel model, Path file) throws IOException {
    }

    public void writeInstallationConnections(GridModel model, Path file) throws IOException {
    }
}