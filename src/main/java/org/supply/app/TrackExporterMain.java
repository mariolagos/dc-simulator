package org.supply.app;

import com.typesafe.config.Config;
import org.dcsim.DcSimScenarioLoader;
import org.supply.io.export.NetworkInputCsvWriter;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Minimal exporter for the simplified track model.
 *
 * Purpose:
 * - export track-derived CSVs without depending on GridModelLoader
 * - avoid unrelated grid/config issues while developing track
 */
public final class TrackExporterMain {

    private TrackExporterMain() {
    }

    public static void main(String[] args) throws Exception {
        run(args);
    }

    public static void run(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Usage: TrackExporterMain <application.conf> [outputDir]"
            );
        }

        Path confFile = RunLayoutFactory.resolveConfArg(args[0]);
        Path outputRoot = (args.length >= 2) ? Paths.get(args[1]) : null;

        Config scenario = DcSimScenarioLoader.loadScenarioConfig(confFile);
        Config dcsim = DcSimScenarioLoader.requireDcsim(scenario, confFile);

        LoadedTrackModel trackModel = new TrackConfigLoader().load(dcsim);

        Path exportDir = outputRoot != null
                ? outputRoot
                : confFile.getParent().resolve("dc/exports");

        new NetworkInputCsvWriter().writeTrackStations(
                trackModel,
                exportDir.resolve("track_stations.csv")
        );

        NetworkInputCsvWriter writer = new NetworkInputCsvWriter();

        writer.writeTrackStations(
                trackModel,
                exportDir.resolve("track_stations.csv")
        );

        writer.writeTrackSegments(
                trackModel,
                exportDir.resolve("track_segments.csv")
        );

        writer.writeTrackJunctions(
                trackModel,
                exportDir.resolve("track_junctions.csv")
        );

        System.out.println("Track export completed:");
        System.out.println("  " + exportDir.resolve("track_stations.csv"));
        System.out.println("  " + exportDir.resolve("track_segments.csv"));
        System.out.println("  " + exportDir.resolve("track_junctions.csv"));    }
}