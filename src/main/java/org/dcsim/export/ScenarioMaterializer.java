package org.dcsim.export;

import com.typesafe.config.Config;
import org.dcsim.DcSimScenarioLoader;
import org.dcsim.ScenarioHelpers;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridModelLoader;
import org.dcsim.math.Real;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ScenarioMaterializer {

    static final boolean DEBUG_VERBOSITY = false;

    private ScenarioMaterializer() {
    }

    public static void materializeScenario(Path scenarioConf, Path outDir, Path defaultRunExcel, String ignoredTrainId) throws Exception {
        if (outDir == null) {
            throw new IllegalArgumentException("ScenarioMaterializer.materializeScenario: outDir is null");
        }
        if (!outDir.isAbsolute()) {
            throw new IllegalStateException(
                    "ScenarioMaterializer.materializeScenario: outDir must be ABSOLUTE for deterministic output paths. Got: " + outDir);
        }

        Files.createDirectories(outDir);

        Config scenario = DcSimScenarioLoader.loadScenarioConfig(scenarioConf);
        Config dcsim = DcSimScenarioLoader.requireDcsim(scenario, scenarioConf);
        GridModelLoader loader = new GridModelLoader();
        GridModel<Real> model = loader.load(dcsim);

        System.out.println("MODEL nodes=" + model.getNodes().size());
        System.out.println("MODEL devices=" + model.getDevices().size());
        System.out.println("MODEL lines=" + model.getLines().size());

        NetworkInputCsvWriter networkWriter = new NetworkInputCsvWriter();
        networkWriter.writeAll(dcsim, model, outDir);

        List<Path> runExcels = new ArrayList<>();
        List<String> trainIds = new ArrayList<>();
        List<Integer> departureTimes = new ArrayList<>();

        int departureTime;
        if (dcsim.hasPath("traffic.timetable.trains")) {
            List<? extends Config> trains = dcsim.getConfig("traffic.timetable").getConfigList("trains");

            for (Config tr : trains) {
                String trainId = tr.getString("id");
                trainIds.add(trainId);
                departureTime = ScenarioHelpers.parseHmsToSeconds(tr.getString("departure"));

                Path runExcel;
                if (tr.hasPath("runExcel")) {

                    Path raw = Paths.get(tr.getString("runExcel"));
                    if (raw.isAbsolute()) {
                        runExcel = raw.normalize();
                    } else {
                        Path confDir = scenarioConf.toAbsolutePath().normalize().getParent();
                        runExcel = confDir.resolve(raw).normalize();
                    }
                } else {
                    runExcel = defaultRunExcel;
                }

                if (runExcel == null || !Files.exists(runExcel)) {
                    throw new IllegalArgumentException("Run Excel not found for train " + trainId + ": " + runExcel);
                }

                departureTimes.add(departureTime);
                runExcels.add(runExcel);
            }
        } else {
            if (defaultRunExcel == null || !Files.exists(defaultRunExcel)) {
                throw new IllegalArgumentException("No traffic.timetable.trains and default runExcel not found: " + defaultRunExcel);
            }
            runExcels.add(defaultRunExcel);
            trainIds.add(ignoredTrainId != null ? ignoredTrainId : "T1");
        }

        if (DEBUG_VERBOSITY) {
            for (int i = 0; i < trainIds.size(); i++) {
                System.out.println("RUNCSV trainId=" + trainIds.get(i) + " file=" + runExcels.get(i));
            }
            System.out.println("RUNCSV about to resolve exportResolution_s");
        }

        double exportResolutionS = dcsim.hasPath("export.exportResolution_s")
                ? dcsim.getDouble("export.exportResolution_s")
                : 0.0;

        if (DEBUG_VERBOSITY) {
            System.out.println("RUNCSV about to write: trains=" + trainIds.size() + " files=" + runExcels.size());
            System.out.println("RUNCSV departures=" + departureTimes);
        }

        RunCsvFromExcel.writeRunCsv(
                runExcels,
                trainIds,
                outDir.resolve("run.csv"),
                departureTimes,
                exportResolutionS
        );
        Path p = outDir.resolve("run.csv").toAbsolutePath();

        if (DEBUG_VERBOSITY) {
            System.out.println("RUNCSV exists=" + Files.exists(p));
            System.out.println("RUNCSV size=" + (Files.exists(p) ? Files.size(p) : -1));
            System.out.println("RUNCSV modified=" + (Files.exists(p) ? Files.getLastModifiedTime(p) : "n/a"));
            System.out.println("RUNCSV written: " + p);
        }
    }

}