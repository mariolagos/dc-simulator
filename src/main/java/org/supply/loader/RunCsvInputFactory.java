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
            throw new IllegalArgumentException("Missing required config: traffic.timetable.trains");
        }

        Config traffic = dcsim.getConfig("traffic");
        Config timetable = traffic.getConfig("timetable");

        for (Config train : timetable.getConfigList("trains")) {
            String trainId = train.getString("id");
            String sectionId = train.getString("sectionId");
            String trackId = train.getString("trackId");
            String templateId = getString(train, "template_id", "templateId");
            int departureSec = TimeUtils.parseHmsToSeconds(train.getString("departure"));

            Config template = traffic.getConfig("templates").getConfig(templateId);

            Config powerProfiles = dcsim.getConfig("powerProfiles");

            Config templateConfig = powerProfiles
                    .getConfigList("templates")
                    .stream()
                    .filter(t -> t.getString("id").equals(templateId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Power profile template not found: " + templateId
                    ));

            String folderText = templateConfig.getString("folder");
            Path templateFolder = resolveTemplateFolder(confFile, folderText);

            Path runExcel = templateFolder.resolve("A-B.xlsx").normalize();

            System.out.println(
                    "train=" + trainId
                            + " template=" + templateId
                            + " folder=" + templateFolder
                            + " runExcel=" + runExcel
            );

            if (!Files.exists(runExcel)) {
                throw new IllegalArgumentException(
                        "Run Excel not found for train " + trainId + ": " + runExcel
                );
            }

            trainIds.add(trainId);
            sectionIds.add(sectionId);
            trackIds.add(trackId);
            departureTimes.add(departureSec);
            runExcels.add(runExcel);
        }

        double exportResolutionS = dcsim.hasPath("export.exportResolution_s")
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