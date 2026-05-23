package org.dcsim.export;

import com.typesafe.config.Config;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class NetworkInputCsvWriter {

    private static final int C1_LEGACY_NODE_EXPORT_OFFSET_M = -100;
    static final boolean DEBUG_VERBOSITY = false;

    /**
     * Preferred entry point.
     * Writes both model-derived files and config-derived power installation files.
     */
    public void writeAll(Config dcsim, GridModel<Real> model, Path outDir) throws IOException {
        if (outDir == null) {
            throw new IllegalArgumentException("NetworkInputCsvWriter.writeAll: outDir is null");
        }
        if (!outDir.isAbsolute()) {
            throw new IllegalStateException(
                    "NetworkInputCsvWriter.writeAll: outDir must be ABSOLUTE for deterministic output paths. Got: " + outDir);
        }

        Files.createDirectories(outDir);

        if (DEBUG_VERBOSITY) {
            System.out.println("WRITEALL lines=" + model.getLines().size());
            System.out.println("WRITEALL devices=" + model.getDevices().size());
        }

        writeNodes(dcsim, model, outDir.resolve("nodes.csv"));
        writeLines(dcsim, model, outDir.resolve("lines.csv"));
        writePowerInstallations(dcsim, model, outDir.resolve("powerInstallations.csv"));
        writeInstallationConnections(dcsim, model, outDir.resolve("installationConnections.csv"));
        writeSystemParameters(dcsim, model, outDir.resolve("systemParameters.csv"));
    }

    private void writeSystemParameters(Config dcsim, GridModel<Real> model, Path file) throws IOException {
        Config grid = dcsim.getConfig("grid");

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("key,value\n");

            if (grid.hasPath("u_nominal_V")) {
                w.write("u_nominal_V," + grid.getDouble("u_nominal_V") + "\n");
            }

            if (grid.hasPath("u_min_V")) {
                w.write("u_min_V," + grid.getDouble("u_min_V") + "\n");
            }

            if (grid.hasPath("u_max_V")) {
                w.write("u_max_V," + grid.getDouble("u_max_V") + "\n");
            }

            if (grid.hasPath("u_cutoff_V")) {
                w.write("u_cutoff_V," + grid.getDouble("u_cutoff_V") + "\n");
            }

            if (grid.hasPath("i_train_max_A")) {
                w.write("i_train_max_A," + grid.getDouble("i_train_max_A") + "\n");
            }
        }
    }

    private void writeNodes(Config dcsim, GridModel<Real> model, Path file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("node_id,track_id,position_m\n");
            for (Node<Real> n : model.getNodes()) {
                String nodeId = n.getNameOrDefault();

                if ("GND".equalsIgnoreCase(nodeId) || "ANCHOR".equalsIgnoreCase(nodeId)) {
                    continue;
                }

                w.write(nodeId + "," + n.getTrackId() + "," + (n.getPositionM() + C1_LEGACY_NODE_EXPORT_OFFSET_M) + "\n");
            }
        }
    }

    private void writeLines(Config dcsim, GridModel<Real> model, Path file) throws IOException {
        // TODO(#27): Temporary C1 legacy offset.
        // Remove when track/path/model position transform is implemented.
        // Then update C1 config positions instead of shifting here.
        if (DEBUG_VERBOSITY) {
            System.out.println("writeLines: lines=" + model.getLines().size());
        }

        Map<Integer, String> nodeNames = new HashMap<>();
        for (Node<Real> n : model.getNodes()) {
            nodeNames.put(n.get_internal_id(), n.getNameOrDefault());
        }

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("node_from_id,node_to_id,resistance_per_m\n");

            for (Object o : (Collection<?>) model.getDevices()) {
                Device<?> d = (Device<?>) o;
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

                    if ("GND".equalsIgnoreCase(fromNode) || "GND".equalsIgnoreCase(toNode)) {
                        continue;
                    }
                    if ("ANCHOR".equalsIgnoreCase(fromNode) || "ANCHOR".equalsIgnoreCase(toNode)) {
                        continue;
                    }

                    double lengthM = ln.getLength();
                    double resistancePerM = lengthM > 0.0
                            ? ln.getResistance().asDouble() / lengthM
                            : 0.0;

                    w.write(fromNode + "," + toNode + "," + resistancePerM + "\n");
                }
            }
        }
    }

    private void writePowerInstallations(Config dcsim, GridModel<Real> model, Path file) throws IOException {
        Config grid = dcsim.getConfig("grid");

        if (DEBUG_VERBOSITY) {
            System.out.println("HAS power_installations=" + grid.hasPath("power_installations"));
            if (grid.hasPath("power_installations")) {
                System.out.println("power_installations count=" + grid.getConfigList("power_installations").size());
            }
        }

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("installation_id,installation_type,emf_V,internal_resistance_ohm,rectifier_type\n");

            if (!grid.hasPath("power_installations")) {
                return;
            }

            List<? extends Config> installations = grid.getConfigList("power_installations");

            for (Config pi : installations) {
                String installationId = pi.getString("installation_id");
                String installationType = pi.getString("installation_type");
                String emfV = pi.hasPath("emf_v")
                        ? String.valueOf(pi.getDouble("emf_v"))
                        : "";
                String internalResistance = pi.hasPath("internal_resistance_ohm")
                        ? String.valueOf(pi.getDouble("internal_resistance_ohm"))
                        : "";
                String rectifierType = pi.hasPath("rectifier_type")
                        ? pi.getString("rectifier_type")
                        : "";

                w.write(String.join(",",
                        installationId,
                        installationType,
                        emfV,
                        internalResistance,
                        rectifierType
                ));
                w.write("\n");
            }
        }
    }

    private void writeInstallationConnections(Config dcsim, GridModel<Real> model, Path file) throws IOException {
        Config grid = dcsim.getConfig("grid");

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("installation_id,node_id,connection_type\n");

            if (!grid.hasPath("power_installations") || !grid.hasPath("power_connections")) {
                return;
            }

            Set<String> installationIds = new HashSet<>();
            List<? extends Config> installations = grid.getConfigList("power_installations");
            for (Config pi : installations) {
                installationIds.add(pi.getString("installation_id"));
            }

            if (DEBUG_VERBOSITY) {
                System.out.println("HAS power_connections=" + grid.hasPath("power_connections"));
                if (grid.hasPath("power_connections")) {
                    System.out.println("power_connections count=" + grid.getConfigList("power_connections").size());
                }
            }

            List<? extends Config> connections = grid.getConfigList("power_connections");
            for (Config pc : connections) {
                String installationId = pc.getString("installation_id");
                if (!installationIds.contains(installationId)) {
                    continue;
                }

                String nodeId = pc.getString("node_id");
                String connectionType = pc.getString("connection_type");

                if ("GND".equalsIgnoreCase(nodeId)) {
                    continue;
                }

                w.write(String.join(",",
                        installationId,
                        nodeId,
                        connectionType
                ));
                w.write("\n");
            }
        }
    }

    /**
     * Extracts [minM,maxM] from whatever type ContractChecks.extentByTrackFromModel returns.
     * Supports a few common shapes:
     * - double[]{min,max}
     * - java.util.List/Collection of two numbers
     * - record/object with methods: minM/maxM, min/max, getMinM/getMaxM, getMin/getMax
     * - object with fields: minM/maxM, min/max
     * <p>
     * If none match, throws with a clear message.
     */
    private static double[] extractMinMaxM(Object extent) {
        if (extent == null) {
            throw new IllegalStateException("extent is null");
        }

        if (extent instanceof double[] arr) {
            if (arr.length < 2) throw new IllegalStateException("extent double[] length < 2");
            return new double[]{arr[0], arr[1]};
        }

        if (extent instanceof java.util.List<?> list) {
            if (list.size() < 2) throw new IllegalStateException("extent list size < 2");
            return new double[]{toDouble(list.get(0)), toDouble(list.get(1))};
        }
        if (extent instanceof Collection<?> coll) {
            if (coll.size() < 2) throw new IllegalStateException("extent collection size < 2");
            Object[] a = coll.toArray();
            return new double[]{toDouble(a[0]), toDouble(a[1])};
        }

        String[][] methodPairs = new String[][]{
                {"minM", "maxM"},
                {"min", "max"},
                {"getMinM", "getMaxM"},
                {"getMin", "getMax"}
        };
        for (String[] pair : methodPairs) {
            Double a = tryInvokeNoArgDouble(extent, pair[0]);
            Double b = tryInvokeNoArgDouble(extent, pair[1]);
            if (a != null && b != null) return new double[]{a, b};
        }

        String[][] fieldPairs = new String[][]{
                {"minM", "maxM"},
                {"min", "max"}
        };
        for (String[] pair : fieldPairs) {
            Double a = tryReadFieldDouble(extent, pair[0]);
            Double b = tryReadFieldDouble(extent, pair[1]);
            if (a != null && b != null) return new double[]{a, b};
        }

        throw new IllegalStateException(
                "Unsupported extent type from ContractChecks.extentByTrackFromModel: " +
                        extent.getClass().getName() +
                        ". Please adapt extractMinMaxM(...) to the actual extent type.");
    }

    private static Double tryInvokeNoArgDouble(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);
            return toDouble(v);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Double tryReadFieldDouble(Object target, String fieldName) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(target);
            return toDouble(v);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return null;
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) throw new IllegalStateException("extent value is null");
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("extent value not numeric: " + v + " (" + v.getClass().getName() + ")");
        }
    }
}