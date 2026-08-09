package org.supply.app;

import com.typesafe.config.Config;
import org.supply.loader.DcSimConfigLoader;

import java.nio.file.Path;

public final class DcStudyContextLoader {

    private DcStudyContextLoader() {
    }

    public static DcStudyContext load(String confArg) {
        Path confFile =
                ExecutionLayoutFactory.resolveConfArg(confArg);

        Path workingDirectory =
                Path.of("")
                        .toAbsolutePath()
                        .normalize();

        Config scenario =
                DcSimConfigLoader.loadScenarioConfig(confFile);

        Config dcsim =
                DcSimConfigLoader.requireDcsim(
                        scenario,
                        confFile
                );

        Config study = scenario.getConfig("study");

        String studyId = study.getString("id");
        String studyName = study.getString("name");
        String studyDescription =
                study.hasPath("description")
                        ? study.getString("description")
                        : "";

        Path dcDirectory =
                workingDirectory.resolve("dc");

        Path exportDirectory =
                dcDirectory.resolve("exports");

        Path resultDirectory =
                dcDirectory.resolve("results");

        return new DcStudyContext(
                confFile,
                workingDirectory,
                scenario,
                dcsim,
                studyId,
                studyName,
                studyDescription,
                exportDirectory,
                resultDirectory
        );
    }
}