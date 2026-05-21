package org.supply.io.export;

import com.typesafe.config.Config;
import org.supply.model.GridModel;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.ModelCoordinate;
import org.supply.track.RouteSegment;
import org.supply.track.RwyCoordinate;
import org.supply.track.Station;
import org.supply.track.TrackJunction;
import org.supply.track.TrackSection;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NetworkInputCsvWriter {

    private static final int C1_LEGACY_NODE_EXPORT_OFFSET_M = -100;
    static final boolean DEBUG_VERBOSITY = false;

    public void writeAll(Config dcsim, GridModel model, LoadedTrackModel trackModel, Path exportDir) throws IOException {
        writeNodes(dcsim, exportDir.resolve("nodes.csv"));
        writeLines(dcsim, exportDir.resolve("lines.csv"));
        writePowerInstallations(dcsim, exportDir.resolve("powerInstallations.csv"));
        writeInstallationConnections(dcsim, exportDir.resolve("installationConnections.csv"));
        writeSystemParameters(dcsim, exportDir.resolve("systemParameters.csv"));

        writeTrackStations(trackModel, exportDir.resolve("track_stations.csv"));
        writeTrackSegments(trackModel, exportDir.resolve("track_segments.csv"));
        writeTrackJunctions(trackModel, exportDir.resolve("track_junctions.csv"));
    }

    public void writeSystemParameters(Config dcsim, Path file) throws IOException {
        createParent(file);

        Config grid = dcsim.getConfig("grid");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("parameter,value");
            writer.newLine();

            writeParameter(writer, "u_nominal_V", grid.getDouble("u_nominal_V"));
            writeParameter(writer, "u_min_V", grid.getDouble("u_min_V"));
            writeParameter(writer, "u_cutoff_V", grid.getDouble("u_cutoff_V"));
            writeParameter(writer, "u_max_V", grid.getDouble("u_max_V"));
            writeParameter(writer, "i_train_max_A", grid.getDouble("i_train_max_A"));
        }
    }

    private void writeParameter(BufferedWriter writer, String name, double value) throws IOException {
        writer.write(name + "," + value);
        writer.newLine();
    }
    public void writeNodes(Config dcsim, Path file) throws IOException {
        createParent(file);

        Config grid = dcsim.getConfig("grid");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("node_id,track_id,position_m");
            writer.newLine();

            for (Config node : grid.getConfigList("nodes")) {
                String nodeId = node.getString("node_id");
                RwyCoordinate rwy = parseRwyCoordinate(node.getString("position_rwy"));
                writer.write(nodeId + "," + rwy.getSectionId() + "," + rwy.getPositionM());
                writer.newLine();
            }
        }
    }

    public void writeLines(Config dcsim, Path file) throws IOException {
        createParent(file);

        Config grid = dcsim.getConfig("grid");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("node_from_id,node_to_id,resistance_per_m");
            writer.newLine();

            for (Config line : grid.getConfigList("lines")) {
                writer.write(
                        line.getString("node_from_id") + "," +
                                line.getString("node_to_id") + "," +
                                line.getDouble("resistance_ohm_per_m")
                );
                writer.newLine();
            }
        }
    }

    public void writePowerInstallations(Config dcsim, Path file) throws IOException {
        createParent(file);

        Config grid = dcsim.getConfig("grid");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("installation_id,installation_type,emf_V,internal_resistance_ohm,rectifier_type");
            writer.newLine();

            for (Config installation : grid.getConfigList("power_installations")) {
                writer.write(
                        installation.getString("installation_id") + "," +
                                installation.getString("installation_type") + "," +
                                installation.getDouble("emf_V") + "," +
                                installation.getDouble("internal_resistance_ohm") + "," +
                                installation.getString("rectifier_type")
                );
                writer.newLine();
            }
        }
    }

    public void writeInstallationConnections(Config dcsim, Path file) throws IOException {
        createParent(file);

        Config grid = dcsim.getConfig("grid");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("installation_id,node_id,connection_type");
            writer.newLine();

            for (Config connection : grid.getConfigList("installation_connections")) {
                writer.write(
                        connection.getString("installation_id") + "," +
                                connection.getString("node_id") + "," +
                                connection.getString("connection_type")
                );
                writer.newLine();
            }
        }
    }

    public void writeTrackStations(LoadedTrackModel trackModel, Path file) throws IOException {

        DefaultTrackTransformService trackService = new DefaultTrackTransformService(trackModel);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {

            // header
            writer.write("name,position_rwy,model_position_m");
            writer.newLine();

            for (Station station : trackModel.getStations()) {

                RwyCoordinate rwy = station.getPosition();

                int modelPositionM;
                try {
                    ModelCoordinate model = trackService.toModel(rwy.getSectionId(), rwy);
                    modelPositionM = model.getPositionM();
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to map station: " + station.getName() + " @ " + rwy,
                            e
                    );
                }

                writer.write(
                        station.getName() + "," +
                                rwy.toDisplayString() + "," +
                                modelPositionM
                );
                writer.newLine();
            }
        }
    }

    public void writeTrackSegments(LoadedTrackModel trackModel, Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("section,from_rwy,to_rwy,start_model_m,length_m");
            writer.newLine();

            for (TrackSection section : trackModel.getSectionsById().values()) {
                for (RouteSegment segment : section.getSegments()) {
                    writer.write(
                            section.getSectionId() + "," +
                                    segment.getStartRwy().toDisplayString() + "," +
                                    segment.getEndRwy().toDisplayString() + "," +
                                    segment.getStartModelM() + "," +
                                    segment.getLengthM()
                    );
                    writer.newLine();
                }
            }
        }
    }

    public void writeTrackJunctions(LoadedTrackModel trackModel, Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("from_section,from_rwy,to_section,to_rwy");
            writer.newLine();

            System.out.println("junctions.size: " + trackModel.getJunctions().size());
            for (TrackJunction junction : trackModel.getJunctions()) {
                RwyCoordinate from = junction.getFrom();
                RwyCoordinate to = junction.getTo();

                System.out.println("writing junction row: "
                        + from.getSectionId() + ","
                        + from.toDisplayString() + ","
                        + to.getSectionId() + ","
                        + to.toDisplayString());

                writer.write(
                        from.getSectionId() + "," +
                                from.toDisplayString() + "," +
                                to.getSectionId() + "," +
                                to.toDisplayString()
                );
                writer.newLine();
            }
        }
    }

    private void createParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private RwyCoordinate parseRwyCoordinate(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid railway coordinate: " + raw);
        }

        String sectionId = parts[0];
        String positionText = parts[1];
        String trackId = parts.length == 3 ? parts[2] : null;

        int positionM = parseKmTextToMeters(positionText);

        return new RwyCoordinate(sectionId, positionText, positionM, trackId);
    }

    private int parseKmTextToMeters(String kmText) {
        String[] parts = kmText.split("\\+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid km format: " + kmText);
        }

        int km = Integer.parseInt(parts[0]);
        int meters = Integer.parseInt(parts[1]);

        return km * 1000 + meters;
    }
}