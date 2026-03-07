package org.dcsim.export;

import org.dcsim.contracts.ContractChecks;
import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.math.Real;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public final class NetworkInputCsvWriter {

    public void writeAll(GridModel<Real> model, Path outDir) throws IOException {
        if (outDir == null) {
            throw new IllegalArgumentException("NetworkInputCsvWriter.writeAll: outDir is null");
        }
        if (!outDir.isAbsolute()) {
            throw new IllegalStateException(
                    "NetworkInputCsvWriter.writeAll: outDir must be ABSOLUTE for deterministic output paths. Got: " + outDir);
        }

        Files.createDirectories(outDir);

        writeTrack(model, outDir.resolve("track.csv"));
        writeNodes(model, outDir.resolve("nodes.csv"));
        writeLines(model, outDir.resolve("lines.csv"));
        writeSubstations(model, outDir.resolve("substations.csv"));
        writeSections(model, outDir.resolve("sections.csv"));
    }

    private void writeTrack(GridModel<Real> model, Path file) throws IOException {
        // You already use this in DcSimApp
        Map<Integer, ?> extentByTrack = ContractChecks.extentByTrackFromModel(model);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("track_id,start_m,end_m\n");
            for (var e : extentByTrack.entrySet()) {
                int trackId = e.getKey();
                double[] mm = extractMinMaxM(e.getValue());
                w.write(trackId + "," + mm[0] + "," + mm[1] + "\n");
            }
        }
    }

    private void writeNodes(GridModel<Real> model, Path file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("node_id,track_id,pos_m,kind\n");
            for (Node<Real> n : model.getNodes()) {
                w.write(n.getId() + "," + n.getTrackId() + "," + n.getPositionM() + "," + n.getNodeKind() + "\n");
            }
        }
    }

    private void writeLines(GridModel<Real> model, Path file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("from_node,to_node,length_m,resistance_ohm\n");
            for (Object o : (Collection<?>) model.getDevices()) {
                Device<?> d = (Device<?>) o;
                if (d instanceof Line ln) {
                    int from = ln.getFromNode();
                    int to = ln.getToNode();
                    double lengthM = ln.getLength();
                    double rOhm = ln.getResistance().asDouble();
                    w.write(from + "," + to + "," + lengthM + "," + rOhm + "\n");
                }
            }
        }
    }

    private void writeSubstations(GridModel<Real> model, Path file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("substation_id,node_id\n");
            int idx = 1;
            for (Object o : (Collection<?>) model.getDevices()) {
                Device<?> d = (Device<?>) o;
                if (d instanceof Substation ss) {
                    int nodeId = ss.getFromNode();
                    w.write(idx + "," + nodeId + "\n");
                    idx++;
                }
            }
        }
    }

    private void writeSections(GridModel<Real> model, Path file) throws IOException {
        // Minimal: one section per track == track extent
        Map<Integer, ?> extentByTrack = ContractChecks.extentByTrackFromModel(model);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("section_id,track_id,start_m,end_m\n");
            for (var e : extentByTrack.entrySet()) {
                int trackId = e.getKey();
                double[] mm = extractMinMaxM(e.getValue());
                int sectionId = trackId;
                w.write(sectionId + "," + trackId + "," + mm[0] + "," + mm[1] + "\n");
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

        // double[2]
        if (extent instanceof double[] arr) {
            if (arr.length < 2) throw new IllegalStateException("extent double[] length < 2");
            return new double[]{arr[0], arr[1]};
        }

        // List/Collection with 2 elements
        if (extent instanceof java.util.List<?> list) {
            if (list.size() < 2) throw new IllegalStateException("extent list size < 2");
            return new double[]{toDouble(list.get(0)), toDouble(list.get(1))};
        }
        if (extent instanceof Collection<?> coll) {
            if (coll.size() < 2) throw new IllegalStateException("extent collection size < 2");
            Object[] a = coll.toArray();
            return new double[]{toDouble(a[0]), toDouble(a[1])};
        }

        // Methods
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

        // Fields
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
        // Try parse string
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("extent value not numeric: " + v + " (" + v.getClass().getName() + ")");
        }
    }
}
