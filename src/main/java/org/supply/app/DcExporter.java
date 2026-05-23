package org.supply.app;

import com.typesafe.config.Config;
import org.supply.io.export.NetworkInputCsvWriter;
import org.supply.io.export.RunCsvWriter;
import org.supply.loader.DcSimConfigLoader;
import org.supply.loader.GridModelLoader;
import org.supply.model.GridModel;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;


import java.nio.file.Path;

public final class DcExporter {

    public static void main(String[] args) throws Exception {
        run(args);
    }

    public static void run(String[] args) throws Exception {
        ExecutionLayout layout = ExecutionLayoutFactory.fromCliArgs(
                args[0],
                args.length >= 2 ? args[1] : null
        );

        Path confFile = ExecutionLayoutFactory.resolveConfArg(args[0]);

        Path exportDir = (args.length >= 2)
                ? ExecutionLayoutFactory.resolvePathArg(args[1])
                : confFile.getParent().resolve("dc").resolve("exports").normalize();

        Config scenario = DcSimConfigLoader.loadScenarioConfig(confFile);
        Config dcsim = DcSimConfigLoader.requireDcsim(scenario, confFile);

        GridModel model = new GridModelLoader().load(dcsim);

        LoadedTrackModel trackModel = new TrackConfigLoader().load(dcsim);

        new NetworkInputCsvWriter().writeAll(dcsim, model, trackModel, exportDir);

        new RunCsvWriter().write(dcsim, confFile, exportDir);

        // Temporary sanity output during integration
        System.out.println("Loaded track sections: " + trackModel.getSectionsById().keySet());
        System.out.println("Loaded track junctions: " + trackModel.getJunctions().size());
        System.out.println("Loaded track stations: " + trackModel.getStations().size());
    }
}