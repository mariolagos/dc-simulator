package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.RunCsvInput;
import org.supply.domain.RunColumn;
import org.supply.domain.RunLogFormat;
import org.supply.domain.RunSource;
import org.supply.io.export.RunCsvFromExcel;
import org.supply.utils.TimeUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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
        List<Boolean> motoringAndAuxiliariesInSameModel = new ArrayList<>();
        List<Double> auxiliaryPowersW = new ArrayList<>();
        List<RunSource> runSources = new ArrayList<>();

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

        Map<String, Integer> synchronizedDepartures = new HashMap<>();
        String synchronizationStation = null;
        if (timetable.hasPath("synchronization")) {
            Config synchronization = timetable.getConfig("synchronization");
            synchronizationStation = synchronization.getString("station");
            if (synchronizationStation.isBlank()) {
                throw new IllegalArgumentException("Synchronization station must not be blank");
            }
            for (Config entry : synchronization.getConfigList("trains")) {
                String id = entry.getString("id");
                int atStation = TimeUtils.parseHmsToSeconds(entry.getString("first_departure"));
                if (synchronizedDepartures.putIfAbsent(id, atStation) != null) {
                    throw new IllegalArgumentException("Duplicate synchronization train id: " + id);
                }
            }
        }
        Set<String> matchedSynchronizationIds = new HashSet<>();

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

            List<? extends Config> legs = templateConfig.hasPath("folder")
                    ? FolderTemplateLegs.read(templateConfig, resolveTemplateFolder(
                    confFile, templateConfig.getString("folder")))
                    : templateConfig.hasPath("legs")
                    ? templateConfig.getConfigList("legs")
                    : List.of(templateConfig);

            if (legs.isEmpty()) {
                throw new IllegalArgumentException(
                        "Template " + templateId + " must contain at least one leg"
                );
            }

            int departureSec;
            if (synchronizedDepartures.containsKey(trainId)) {
                matchedSynchronizationIds.add(trainId);
                double offsetS = synchronizationOffsetS(
                        legs, templateConfig, confFile, synchronizationStation, templateId);
                double originS = synchronizedDepartures.get(trainId) - offsetS;
                if (!Double.isFinite(originS) || Math.abs(originS - Math.rint(originS)) > 1e-9
                        || originS < Integer.MIN_VALUE || originS > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Synchronization produces non-integral or out-of-range origin for " + trainId
                                    + ": " + originS);
                }
                departureSec = (int) Math.rint(originS);
            } else {
                departureSec = TimeUtils.parseHmsToSeconds(train.getString("departure"));
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

                    boolean logSource = leg.hasPath("run_log");
                    if (logSource == leg.hasPath("run_excel")) {
                        throw new IllegalArgumentException(
                                "Exactly one of run_excel and run_log is required for template "
                                        + templateId + ", leg " + (legIndex + 1)
                        );
                    }

                    String runExcelSheet = "";
                    Path runFile;
                    RunSource runSource;
                    if (logSource) {
                        Config log = leg.getConfig("run_log");
                        runFile = resolveRunFile(confFile, log.getString("file"));
                        runSource = RunSource.log(runFile, readLogFormat(log));
                    } else {
                        runExcelSheet = leg.hasPath("run_excel_sheet")
                                ? leg.getString("run_excel_sheet") : "run";
                        runFile = resolveRunFile(confFile, leg.getString("run_excel"));
                        runSource = RunSource.excel(runFile, runExcelSheet);
                    }

                    if (!Files.exists(runFile)) {
                        throw new IllegalArgumentException(
                                "Run source not found for train "
                                        + expandedTrainId
                                        + ", template "
                                        + templateId
                                        + ", leg "
                                        + (legIndex + 1)
                                        + ": "
                                        + runFile
                        );
                    }

                    Integer relativeLegDepartureSec =
                            leg.hasPath("departure")
                                    ? TimeUtils.parseHmsToSeconds(
                                    leg.getString("departure")
                            )
                                    : null;

                    boolean sameModel = logSource ? true : getOptionalBoolean(
                            leg, templateConfig,
                            "motoring_and_auxiliaries_in_same_model", true
                    );
                    double auxiliaryPowerW = logSource ? 0.0 : getOptionalDouble(
                            leg, templateConfig, "auxiliary_power_W", 0.0
                    );
                    if (logSource && (leg.hasPath("auxiliary_power_W")
                            || leg.hasPath("motoring_and_auxiliaries_in_same_model")
                            || templateConfig.hasPath("auxiliary_power_W")
                            || templateConfig.hasPath(
                            "motoring_and_auxiliaries_in_same_model"))) {
                        throw new IllegalArgumentException(
                                "Auxiliary-power options are not valid for measured run_log "
                                        + runFile
                        );
                    }
                    if (auxiliaryPowerW < 0.0) {
                        throw new IllegalArgumentException(
                                "auxiliary_power_W must be >= 0 for train "
                                        + expandedTrainId
                                        + ", template "
                                        + templateId
                                        + ", leg "
                                        + (legIndex + 1)
                        );
                    }

                    trainIds.add(expandedTrainId);
                    sectionIds.add(getOptionalString(leg, train, "sectionId"));
                    trackIds.add(getOptionalString(leg, train, "trackId"));
                    routeIds.add(routeId);
                    departureTimes.add(expandedDepartureSec);
                    relativeLegDepartureTimes.add(relativeLegDepartureSec);
                    motoringAndAuxiliariesInSameModel.add(sameModel);
                    auxiliaryPowersW.add(auxiliaryPowerW);
                    runExcels.add(runFile);
                    runExcelSheets.add(runExcelSheet);
                    runSources.add(runSource);
                }
            }
        }

        for (String id : synchronizedDepartures.keySet()) {
            if (!matchedSynchronizationIds.contains(id)) {
                throw new IllegalArgumentException("Unknown synchronization train id: " + id);
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
                motoringAndAuxiliariesInSameModel,
                auxiliaryPowersW,
                simulationStartSec,
                simulationEndSec,
                exportResolutionS,
                runSources
        );
    }

    /** Offset of departure from station relative to the first BA leg's time origin. */
    private static double synchronizationOffsetS(
            List<? extends Config> legs, Config template, Path confFile,
            String station, String templateId) throws Exception {
        String prefix = template.hasPath("folder")
                ? template.getString("prefix")
                : "";
        int targetIndex = -1;
        for (int i = 0; i < legs.size(); i++) {
            String filename = Path.of(legs.get(i).getString("run_excel"))
                    .getFileName().toString();
            String pair = filename.substring(prefix.length(), filename.length() - ".xlsx".length());
            String from = pair.substring(0, pair.indexOf('-'));
            if (from.equals(station)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            throw new IllegalArgumentException("Station " + station
                    + " has no outgoing leg in template " + templateId);
        }
        double firstSourceDeparture = sourceDepartureS(legs.get(0), confFile);
        Config target = legs.get(targetIndex);
        if (target.hasPath("departure")) {
            return TimeUtils.parseHmsToSeconds(target.getString("departure"));
        }
        return sourceDepartureS(target, confFile) - firstSourceDeparture;
    }

    private static double sourceDepartureS(Config leg, Path confFile) throws Exception {
        if (!leg.hasPath("run_excel")) {
            throw new IllegalArgumentException("Synchronization requires RunExcel legs");
        }
        Path file = resolveRunFile(confFile, leg.getString("run_excel"));
        String sheet = leg.hasPath("run_excel_sheet") ? leg.getString("run_excel_sheet") : "run";
        var read = new RunCsvFromExcel().read(RunSource.excel(file, sheet));
        return read.relativeDepartureS() != null
                ? read.relativeDepartureS() : read.samples().get(0).timeS();
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

    private static Path resolveRunFile(Path confFile, String fileText) {
        Path raw = Paths.get(fileText);

        if (raw.isAbsolute()) {
            return raw.normalize();
        }

        Path confDir = confFile.toAbsolutePath().normalize().getParent();
        return confDir.resolve(raw).normalize();
    }

    private static RunLogFormat readLogFormat(Config log) {
        Config columns = log.getConfig("columns");
        String delimiterText = log.hasPath("delimiter")
                ? log.getString("delimiter") : ";";
        char delimiter = "\\t".equals(delimiterText)
                ? '\t' : singleCharacter(delimiterText, "delimiter");
        String sign = log.hasPath("power_sign")
                ? log.getString("power_sign") : "consumption_positive";
        if (!sign.equals("consumption_positive")
                && !sign.equals("regeneration_positive")) {
            throw new IllegalArgumentException(
                    "power_sign must be consumption_positive or regeneration_positive"
            );
        }
        return new RunLogFormat(
                delimiter,
                readColumn(columns, "time", true),
                readColumn(columns, "position", false),
                readColumn(columns, "speed", false),
                readColumn(columns, "power", false),
                readColumn(columns, "voltage", false),
                readColumn(columns, "current", false),
                sign.equals("consumption_positive")
        );
    }

    private static RunColumn readColumn(
            Config columns,
            String quantity,
            boolean required
    ) {
        if (!columns.hasPath(quantity)) {
            if (required) {
                throw new IllegalArgumentException(
                        "Missing required log column mapping: " + quantity
                );
            }
            return null;
        }
        Config column = columns.getConfig(quantity);
        return new RunColumn(
                column.getString("name"),
                column.hasPath("unit") ? column.getString("unit") : "",
                column.hasPath("format") ? column.getString("format") : ""
        );
    }

    private static char singleCharacter(String text, String field) {
        if (text.length() != 1) {
            throw new IllegalArgumentException(field + " must contain one character");
        }
        return text.charAt(0);
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

    private static boolean getOptionalBoolean(
            Config preferred,
            Config fallback,
            String path,
            boolean defaultValue
    ) {
        if (preferred.hasPath(path)) {
            return preferred.getBoolean(path);
        }
        if (fallback.hasPath(path)) {
            return fallback.getBoolean(path);
        }
        return defaultValue;
    }

    private static double getOptionalDouble(
            Config preferred,
            Config fallback,
            String path,
            double defaultValue
    ) {
        if (preferred.hasPath(path)) {
            return preferred.getDouble(path);
        }
        if (fallback.hasPath(path)) {
            return fallback.getDouble(path);
        }
        return defaultValue;
    }

}
