package org.dcsim.export;

import com.typesafe.config.Config;
import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.NodeKind;
import org.dcsim.math.Real;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

            w.write("node_id,position_m\n");
            for (Node<Real> n : model.getNodes()) {
                if (n.getNodeKind() == NodeKind.SUBSTATION)
                    w.write(n.getNameOrDefault() + "," + n.getPositionM() + "\n");
            }
        }
    }

    public static void writeLines(GridModel<Real> model, Path file) throws IOException {
        @SuppressWarnings("unchecked")
        Collection<Device<Real>> devices = (Collection<Device<Real>>) (Collection<?>) model.getDevices();
        ensureParent(file);

        Map<Integer, String> nodeNames = new HashMap<>();
        for (Node<Real> n : model.getNodes()) {
            nodeNames.put(n.get_internal_id(), n.getNameOrDefault());
        }

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)) {
            w.write("from_node,to_node,resistance_ohm\n");

            for (Device<Real> d : devices) {
                if (d instanceof Line ln) {
                    String fromNode = nodeNames.get(ln.getFromNode());
                    String toNode = nodeNames.get(ln.getToNode());

                    if (fromNode == null || toNode == null) {
                        throw new IllegalStateException(
                                "Missing node name for line " + ln.getId() +
                                        " from=" + ln.getFromNode() +
                                        " to=" + ln.getToNode()
                        );
                    }

                    w.write(fromNode + "," +
                            toNode + "," +
                            ln.getResistance().asDouble() + "\n");
                }
            }
        }
    }

    private void writeSubstations(Config dcsim, Path file) throws IOException {
        ensureParent(file);

        Config grid = dcsim.getConfig("grid");

        List<? extends Config> installations =
                grid.getConfigList("power_installations");

        List<? extends Config> connections =
                grid.getConfigList("power_connections");

        Map<String, Config> byId = new LinkedHashMap<>();
        for (Config sub : installations) {
            String id = sub.getString("id");
            byId.putIfAbsent(id, sub);
        }

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)) {
            w.write("substation_id,emf_V,internal_resistance_ohm,rectifier_type\n");

            for (Config sub : byId.values()) {
                String id = sub.getString("id");
                double emf = sub.getDouble("emf");
                double r = sub.getDouble("internalResistance");
                String rect = sub.hasPath("rectifierType") ? sub.getString("rectifierType") : "DIODE";

                w.write(id + "," + emf + "," + r + "," + rect + "\n");
            }
        }
    }

    public static void writeSubstationConnections(Config dcsim, GridModel<Real> model, Path file) throws IOException {
        ensureParent(file);

        Map<Integer, String> nodeNames = new LinkedHashMap<>();
        for (Node<Real> n : model.getNodes()) {
            nodeNames.put(n.get_internal_id(), n.getNameOrDefault());
        }

        Config grid = dcsim.getConfig("grid");
        List<? extends Config> substationList = grid.getConfigList("power_connections");

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)) {
            w.write("substation_id,node_id,connection_type\n");

            for (Config sub : substationList) {
                String substationId = sub.getString("id");
                int nodeId = sub.getInt("nodeId");
                String nodeName = nodeNames.get(nodeId);

                if (nodeName == null) {
                    throw new IllegalStateException("Missing node name for nodeId=" + nodeId + " in substation " + substationId);
                }

                String connectionType = sub.getString("connectionType");
                w.write(substationId + "," + nodeName + "," + connectionType + "\n");
            }
        }
    }

    private static void ensureParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
    }
}
