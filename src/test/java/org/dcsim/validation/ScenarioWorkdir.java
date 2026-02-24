package org.dcsim.validation;

import java.nio.file.Path;

public record ScenarioWorkdir(
        String scenarioId,
        Path workdir,
        ScenarioMode scenarioMode,
        RunsMode runsMode,
        Path applicationConf, // may be null if ScenarioMode.CSV_NETWORK
        Path sectionsCsv,
        Path trackCsv,
        Path nodesCsv,
        Path substationsCsv,
        Path linesCsv,
        Path runCsv
) {
}
