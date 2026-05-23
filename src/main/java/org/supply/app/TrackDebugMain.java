package org.supply.app;

import com.typesafe.config.Config;
import org.supply.loader.DcSimConfigLoader;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;

import java.nio.file.Path;

/**
 * Small debug entry point for the simplified track model.
 *
 * Purpose:
 * - load track config without involving GridModelLoader
 * - verify kilometer_boards / junctions / stations
 * - allow quick toModel sanity checks
 */
public final class TrackDebugMain {

    private TrackDebugMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected scenario config path as first argument");
        }

        Path confFile = ExecutionLayoutFactory.resolveConfArg(args[0]);

        Config scenario = DcSimConfigLoader.loadScenarioConfig(confFile);
        Config dcsim = DcSimConfigLoader.requireDcsim(scenario, confFile);

        LoadedTrackModel trackModel = new TrackConfigLoader().load(dcsim);

        System.out.println("=== TRACK DEBUG ===");
        System.out.println("Sections: " + trackModel.getSectionsById().keySet());
        System.out.println("Junctions: " + trackModel.getJunctions().size());
        System.out.println("Stations: " + trackModel.getStations().size());

        trackModel.getSectionsById().forEach((sectionId, section) -> {
            System.out.println("Section " + sectionId + ": " + section.getSegments().size() + " segments");
            section.getSegments().forEach(segment -> {
                System.out.println("  [" + segment.getSequenceNo() + "] "
                        + segment.getStartRwy() + " -> "
                        + segment.getEndRwy()
                        + " | startModelM=" + segment.getStartModelM()
                        + " | lengthM=" + segment.getLengthM());
            });
        });

        if (!trackModel.getStations().isEmpty()) {
            System.out.println("Stations:");
            trackModel.getStations().forEach(station ->
                    System.out.println("  " + station.getName() + " @ " + station.getPosition())
            );
        }

        if (!trackModel.getJunctions().isEmpty()) {
            System.out.println("Junctions:");
            trackModel.getJunctions().forEach(junction ->
                    System.out.println("  " + junction.getFrom() + " <-> " + junction.getTo())
            );
        }

        // Optional sample toModel check.
        // Adjust or remove if not useful for current scenario.
        DefaultTrackTransformService service = new DefaultTrackTransformService(trackModel);

        System.out.println("=== SAMPLE toModel CHECK ===");
        System.out.println("Create a test coordinate matching your config if needed.");
        // Example:
        // RwyCoordinate sample = new RwyCoordinate("S1", "0+150", 150, null);
        // System.out.println("toModel(" + sample + ") = " + service.toModel("S1", sample));
    }
}