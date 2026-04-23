package org.supply.io.export;

import org.supply.model.GridModel;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.ModelCoordinate;
import org.supply.track.RouteSegment;
import org.supply.track.RwyCoordinate;
import org.supply.track.Station;
import org.supply.track.TrackSection;
import org.supply.track.TrackJunction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NetworkInputCsvWriter {

    public void writeAll(GridModel model, LoadedTrackModel trackModel, Path exportDir) throws IOException {
        writeNodes(model, exportDir.resolve("nodes.csv"));
        writeLines(model, exportDir.resolve("lines.csv"));
        writePowerInstallations(model, exportDir.resolve("powerInstallations.csv"));
        writeInstallationConnections(model, exportDir.resolve("installationConnections.csv"));

        writeTrackStations(trackModel, exportDir.resolve("track_stations.csv"));
        writeTrackSegments(trackModel, exportDir.resolve("track_segments.csv"));
        writeTrackJunctions(trackModel, exportDir.resolve("track_junctions.csv"));
    }

    public void writeNodes(GridModel model, Path file) throws IOException {
    }

    public void writeLines(GridModel model, Path file) throws IOException {
    }

    public void writePowerInstallations(GridModel model, Path file) throws IOException {
    }

    public void writeInstallationConnections(GridModel model, Path file) throws IOException {
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

}