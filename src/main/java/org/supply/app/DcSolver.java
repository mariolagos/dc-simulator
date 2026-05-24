package org.supply.app;

import com.typesafe.config.Config;
import org.supply.domain.RunCsvInput;
import org.supply.domain.SystemParameters;
import org.supply.loader.DcSimConfigLoader;
import org.supply.loader.GridModelLoader;
import org.supply.loader.RunCsvInputFactory;
import org.supply.loader.SystemParametersFactory;
import org.supply.model.GridModel;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;

import java.nio.file.Path;

public final class DcSolver {

    public static void main(String[] args) throws Exception {
        run(args);
    }

    public static void run(String[] args) throws Exception {

        Path confFile =
                ExecutionLayoutFactory.resolveConfArg(args[0]);

        Config scenario =
                DcSimConfigLoader.loadScenarioConfig(confFile);

        Config dcsim =
                DcSimConfigLoader.requireDcsim(scenario, confFile);

        GridModel grid =
                new GridModelLoader().load(dcsim);

        LoadedTrackModel track =
                new TrackConfigLoader().load(dcsim);

        RunCsvInput runInput =
                new RunCsvInputFactory().build(dcsim, confFile);

        SystemParameters systemParameters =
                new SystemParametersFactory().build(dcsim);

        System.out.println("DcSolver startup OK");
    }
}
