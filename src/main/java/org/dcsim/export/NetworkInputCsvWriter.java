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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public final class NetworkInputCsvWriter {

    public void writeAll(GridModel<Real> model, Path outDir) throws IOException {
        Files.createDirectories(outDir);

        writeTrack(model, outDir.resolve("track.csv"));
        writeNodes(model, outDir.resolve("nodes.csv"));
        writeLines(model, outDir.resolve("lines.csv"));
        writeSubstations(model, outDir.resolve("substations.csv"));
        writeSections(model, outDir.resolve("sections.csv"));
    }

    private void writeTrack(GridModel<Real> model, Path file) throws IOException {
        // You already use this in DcSimApp
        var extentByTrack = ContractChecks.extentByTrackFromModel(model);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("track_id,start_m,end_m\n");
            // extentByTrack: trackId -> [min,max] (guess: a record/tuple). Adjust accessors below.
            for (var e : extentByTrack.entrySet()) {
                int trackId = e.getKey();
                var extent = e.getValue();
                double start = extent.minM();  // <- rename to your actual accessor
                double end = extent.maxM();    // <- rename to your actual accessor
                w.write(trackId + "," + start + "," + end + "\n");
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
                    // In your code you treat ss.getFromNode() as a bus node. :contentReference[oaicite:4]{index=4}
                    int nodeId = ss.getFromNode();
                    w.write(idx + "," + nodeId + "\n");
                    idx++;
                }
            }
        }
    }

    private void writeSections(GridModel<Real> model, Path file) throws IOException {
        // Minimal: one section per track == track extent
        var extentByTrack = ContractChecks.extentByTrackFromModel(model);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("section_id,track_id,start_m,end_m\n");
            for (var e : extentByTrack.entrySet()) {
                int trackId = e.getKey();
                var extent = e.getValue();
                double start = extent.minM(); // <- adjust
                double end = extent.maxM();   // <- adjust
                int sectionId = trackId;
                w.write(sectionId + "," + trackId + "," + start + "," + end + "\n");
            }
        }
    }
}
