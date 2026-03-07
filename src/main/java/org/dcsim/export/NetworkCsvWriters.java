package org.dcsim.export;

import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.math.Real;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

public final class NetworkCsvWriters {
    private NetworkCsvWriters() {
    }

    // Name rules: case-insensitive identifiers
    public static String normName(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    // Deterministic node naming used for CSV references
    public static String nodeName(int nodeId) {
        return "N" + nodeId;
    }

    public static void writeNodes(GridModel<Real> model, Path file) throws IOException {
        ensureParent(file);
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)) {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            w.write("name,section,track,position_m\n");
            for (Node<Real> n : model.getNodes()) {
                // section=1 for nodes (run.csv drives section/track from excel)
                w.write(normName(nodeName(n.getId())) + ",1," + n.getTrackId() + "," + n.getPositionM() + "\n");
            }
        }
    }

    public static void writeLines(GridModel<Real> model, Path file) throws IOException {
        @SuppressWarnings("unchecked")
        Collection<Device<Real>> devices = (Collection<Device<Real>>) (Collection<?>) model.getDevices();
        ensureParent(file);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)) {

            w.write("from_node,to_node,length_m,resistance_ohm\n");
            for (Device<Real> d : devices) {
                if (d instanceof Line ln) {
                    w.write(normName(nodeName(ln.getFromNode())) + "," +
                            normName(nodeName(ln.getToNode())) + "," +
                            ln.getLength() + "," +
                            ln.getResistance().asDouble() + "\n");
                }
            }
        }
    }

    public static void writeSubstations(GridModel<Real> model, Path file) throws IOException {
        @SuppressWarnings("unchecked")
        Collection<Device<Real>> devices = (Collection<Device<Real>>) (Collection<?>) model.getDevices();
        ensureParent(file);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)) {
            w.write("id,feed_nodes,return_node,emf,internal_resistance,rectifier_type\n");
            for (Device<Real> d : devices) {
                if (d instanceof Substation ss) {
                    String rect = ss.isAllowBackfeed() ? "BIDIR" : "DIODE";
                    w.write(normName(ss.getId()) + "," +
                            normName(nodeName(ss.getFromNode())) + "," +
                            normName(nodeName(ss.getToNode())) + "," +
                            ss.getEmf().asDouble() + "," +
                            ss.getInternalResistance().asDouble() + "," +
                            rect + "\n");
                }
            }
        }
    }

    private static void ensureParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
    }
}
