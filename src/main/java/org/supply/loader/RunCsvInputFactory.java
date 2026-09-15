package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.RunCsvInput;
import org.supply.utils.TimeUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class RunCsvInputFactory {

    public RunCsvInput build(Config dcsim, Path confFile) throws Exception {

        List<Path> runExcels = new ArrayList<>();
        List<String> runExcelSheets = new ArrayList<>();
        List<String> trainIds = new ArrayList<>();
        List<String> trackIds = new ArrayList<>();
        List<String> routeIds = new ArrayList<>();
        List<String> sectionIds = new ArrayList<>();
        List<Integer> departureTimes = new ArrayList<>();
        List<Integer> relativeLegDepartureTimes = new ArrayList<>();

        if (!dcsim.hasPath("traffic.timetable.trains")) {
            throw new IllegalArgumentException(
                    "Missing required config: traffic.timetable.trains"
            );
        }

        Config traffic =
                dcsim.getConfig("traffic");

        Config timetable =
                traffic.getConfig("timetable");

        Config templates =
                traffic.getConfig("templates");

        for (Config train : timetable.getConfigList("trains")) {

            String trainId =
                    train.getString("id");

            String routeId =
                    train.getString("routeId");

            String templateId =
                    getString(
                            train,
                            "template_id",
                            "templateId"
                    );

            int departureSec =
                    TimeUtils.parseHmsToSeconds(
                            train.getString("departure")
                    );

            int count = train.getInt("count");

            if (count <= 0) {
                throw new IllegalArgumentException(
                        "count must be > 0 for train " + trainId
                );
            }

            int headwaySec = 0;

            if (count > 1) {
                if (!train.hasPath("headway")) {
                    throw new IllegalArgumentException(
                            "Missing required field 'headway' for train "
                                    + trainId
                                    + " with count="
                                    + count
                    );
                }

                headwaySec =
                        TimeUtils.parseHmsToSeconds(
                                train.getString("headway")
                        );
            }

            if (count > 1 && headwaySec <= 0) {
                throw new IllegalArgumentException(
                        "headway must be > 0 for train " + trainId
                );
            }

            Config templateConfig =
                    templates.getConfig(templateId);

            List<? extends Config> legs =
                    templateConfig.hasPath("legs")
                            ? templateConfig.getConfigList("legs")
                            : List.of(templateConfig);

            if (legs.isEmpty()) {
                throw new IllegalArgumentException(
                        "Template " + templateId + " must contain at least one leg"
                );
            }

            for (int i = 0; i < count; i++) {

                String expandedTrainId =
                        count == 1
                                ? trainId
                                : String.format(
                                "%s-%03d",
                                trainId,
                                i + 1
                        );

                int expandedDepartureSec =
                        departureSec + i * headwaySec;

                for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
                    Config leg = legs.get(legIndex);

                    String runExcelText = leg.getString("run_excel");
                    String runExcelSheet =
                            leg.hasPath("run_excel_sheet")
                                    ? leg.getString("run_excel_sheet")
                                    : "run";

                    Path runExcel = resolveRunExcel(confFile, runExcelText);

                    if (!Files.exists(runExcel)) {
                        throw new IllegalArgumentException(
                                "Run Excel not found for train "
                                        + expandedTrainId
                                        + ", template "
                                        + templateId
                                        + ", leg "
                                        + (legIndex + 1)
                                        + ": "
                                        + runExcel
                        );
                    }

                    Integer relativeLegDepartureSec =
                            leg.hasPath("departure")
                                    ? TimeUtils.parseHmsToSeconds(
                                    leg.getString("departure")
                            )
                                    : null;

                    trainIds.add(expandedTrainId);
                    sectionIds.add(getOptionalString(leg, train, "sectionId"));
                    trackIds.add(getOptionalString(leg, train, "trackId"));
                    routeIds.add(routeId);
                    departureTimes.add(expandedDepartureSec);
                    relativeLegDepartureTimes.add(relativeLegDepartureSec);
                    runExcels.add(runExcel);
                    runExcelSheets.add(runExcelSheet);
                }
            }
        }

        double exportResolutionS =
                dcsim.hasPath("export.exportResolution_s")
                        ? dcsim.getDouble("export.exportResolution_s")
                        : 0.0;

        Config simulationControl =
                dcsim.getConfig("simulationControl");

        int simulationStartSec =
                TimeUtils.parseHmsToSeconds(
                        simulationControl.getString("simulationStart")
                );

        int simulationEndSec =
                TimeUtils.parseHmsToSeconds(
                        simulationControl.getString("simulationEnd")
                );

        if (simulationEndSec < simulationStartSec) {
            throw new IllegalArgumentException(
                    "simulationEnd must not be before simulationStart"
            );
        }

        return new RunCsvInput(
                runExcels,
                runExcelSheets,
                trainIds,
                sectionIds,
                trackIds,
                routeIds,
                departureTimes,
                relativeLegDepartureTimes,
                simulationStartSec,
                simulationEndSec,
                exportResolutionS
        );
    }

    private static Path resolveTemplateFolder(
            Path confFile,
            String folderText
    ) {
        Path folder = Path.of(folderText);

        if (folder.isAbsolute()) {
            return folder.normalize();
        }

        Path confDir = confFile.toAbsolutePath().getParent();

        if (confDir == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve relative template folder: " + folderText
            );
        }

        return confDir.resolve(folder).normalize();
    }

    private static Path resolveRunExcel(Path confFile, String runExcelText) {
        Path raw = Paths.get(runExcelText);

        if (raw.isAbsolute()) {
            return raw.normalize();
        }

        Path confDir = confFile.toAbsolutePath().normalize().getParent();
        return confDir.resolve(raw).normalize();
    }

    private static String getString(Config config, String preferred, String legacy) {
        if (config.hasPath(preferred)) {
            return config.getString(preferred);
        }
        if (config.hasPath(legacy)) {
            return config.getString(legacy);
        }
        throw new IllegalArgumentException(
                "Missing required field: " + preferred + " (legacy: " + legacy + ")"
        );
    }

    private static String getOptionalString(
            Config preferred,
            Config fallback,
            String path
    ) {
        if (preferred.hasPath(path)) {
            return preferred.getString(path);
        }
        if (fallback.hasPath(path)) {
            return fallback.getString(path);
        }
        return "";
    }

}
