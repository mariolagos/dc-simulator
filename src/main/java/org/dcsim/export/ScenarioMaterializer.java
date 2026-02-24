package org.dcsim.export;

import com.typesafe.config.Config;
import org.dcsim.DcSimScenarioLoader;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridModelLoader;
import org.dcsim.math.Real;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ScenarioMaterializer {

    public static void materializeScenario(Path scenarioConf, Path outDir, Path runExcel, String trainId) throws Exception {
        Files.createDirectories(outDir);

        Config scenario = DcSimScenarioLoader.loadScenarioConfig(scenarioConf);
        Config dcsim = DcSimScenarioLoader.requireDcsim(scenario, scenarioConf);
        GridModel<Real> model = GridModelLoader.load(dcsim);

        NetworkCsvWriters.writeNodes(model, outDir.resolve("nodes.csv"));
        NetworkCsvWriters.writeLines(model, outDir.resolve("lines.csv"));
        NetworkCsvWriters.writeSubstations(model, outDir.resolve("substations.csv"));

        RunCsvFromExcel.writeRunCsv(runExcel, outDir.resolve("run.csv"), trainId); // din excel→run.csv
    }
}
