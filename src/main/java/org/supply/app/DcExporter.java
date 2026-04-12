package org.supply.app;

import com.typesafe.config.Config;
import org.dcsim.DcSimScenarioLoader;
import org.supply.io.export.NetworkInputCsvWriter;
import org.supply.loader.GridModelLoader;
import org.supply.model.GridModel;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class DcExporter {

    public static void main(String[] args) throws Exception {
        run(args);
    }

    public static void run(String[] args) throws Exception {
        Path confFile = RunLayoutFactory.resolveConfArg(args[0]);
        Path outputRoot = (args.length >= 2) ? Paths.get(args[1]) : null;

        Config scenario = DcSimScenarioLoader.loadScenarioConfig(confFile);
        Config dcsim = DcSimScenarioLoader.requireDcsim(scenario, confFile);

        GridModel model = new GridModelLoader().load(dcsim);

        Path exportDir = outputRoot != null
                ? outputRoot
                : confFile.getParent().resolve("dc/exports");

        new NetworkInputCsvWriter().writeAll(model, exportDir);
    }
}