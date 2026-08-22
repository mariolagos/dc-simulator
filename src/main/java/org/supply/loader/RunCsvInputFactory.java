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
        List<String> trainIds = new ArrayList<>();
        List<String> trackIds = new ArrayList<>();
        List<String> sectionIds = new ArrayList<>();
        List<Integer> departureTimes = new ArrayList<>();

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

            String sectionId =
                    train.getString("sectionId");

            String trackId =
                    train.getString("trackId");

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

            String runExcelText =
                    templateConfig.getString("run_excel");

            Path runExcel =
                    confFile.getParent()
                            .resolve(runExcelText)
                            .normalize();

            if (!Files.exists(runExcel)) {
                throw new IllegalArgumentException(
                        "Run Excel not found for train "
                                + trainId
                                + ": "
                                + runExcel
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

                trainIds.add(expandedTrainId);
                sectionIds.add(sectionId);
                trackIds.add(trackId);
                departureTimes.add(expandedDepartureSec);
                runExcels.add(runExcel);
            }
        }

        double exportResolutionS =
                dcsim.hasPath("export.exportResolution_s")
                        ? dcsim.getDouble("export.exportResolution_s")
                        : 0.0;

        return new RunCsvInput(
                runExcels,
                trainIds,
                sectionIds,
                trackIds,
                departureTimes,
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

}