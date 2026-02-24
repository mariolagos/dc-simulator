package org.dcsim.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.dcsim.validation.ValidationFiles.APPLICATION_CONF;
import static org.dcsim.validation.ValidationFiles.LINES_CSV;
import static org.dcsim.validation.ValidationFiles.NODES_CSV;
import static org.dcsim.validation.ValidationFiles.RUN_CSV;
import static org.dcsim.validation.ValidationFiles.SECTIONS_CSV;
import static org.dcsim.validation.ValidationFiles.SUBSTATIONS_CSV;
import static org.dcsim.validation.ValidationFiles.TRACK_CSV;

public final class ValidationScenarioLoader {

    private final ValidationTestDataFactory factory;

    public ValidationScenarioLoader(ValidationTestDataFactory factory) {
        this.factory = factory;
    }

    /**
     * Materialize a scenario into tempRoot/<scenarioId>/ so it becomes runnable.
     * <p>
     * Supports:
     * - Scenario alt 1: application.conf exists -> try generate 2..6; if not implemented, fallback to existing CSVs if present.
     * - Scenario alt 2: no application.conf -> copy 2..6 from scenario dir.
     * <p>
     * Runs:
     * - If run.csv exists -> copy it
     * - else -> generate it via factory
     */
    public ScenarioWorkdir materialize(String scenarioId, Path scenarioRoot, Path tempRoot) throws IOException {
        Path scenarioDir = scenarioRoot.resolve(scenarioId);
        if (!Files.isDirectory(scenarioDir)) {
            throw new IllegalArgumentException("Scenario dir not found: " + scenarioDir);
        }

        Path workdir = tempRoot.resolve(scenarioId);
        Files.createDirectories(workdir);

        boolean hasAppConf = Files.exists(scenarioDir.resolve(APPLICATION_CONF));
        ScenarioMode scenarioMode = hasAppConf ? ScenarioMode.APP_CONF_GENERATES_NETWORK : ScenarioMode.CSV_NETWORK;

        boolean hasRun = Files.exists(scenarioDir.resolve(RUN_CSV));
        RunsMode runsMode = hasRun ? RunsMode.EXISTING_RUN_CSV : RunsMode.GENERATED_RUN_CSV;

        Path appConfPath = null;

        if (scenarioMode == ScenarioMode.APP_CONF_GENERATES_NETWORK) {
            // Copy application.conf into workdir
            appConfPath = copyFile(scenarioDir.resolve(APPLICATION_CONF), workdir.resolve(APPLICATION_CONF));

            // Try to generate network CSVs (2..6) from app conf
            try {
                factory.generateNetworkCsvsFromAppConf(appConfPath, workdir);
            } catch (UnsupportedOperationException ex) {
                // Fallback: if 2..6 exist in scenario folder, use them directly
                Path sections = scenarioDir.resolve(SECTIONS_CSV);
                Path track = scenarioDir.resolve(TRACK_CSV);
                Path nodes = scenarioDir.resolve(NODES_CSV);
                Path subs = scenarioDir.resolve(SUBSTATIONS_CSV);
                Path lines = scenarioDir.resolve(LINES_CSV);

                if (Files.exists(sections) && Files.exists(track) && Files.exists(nodes)
                        && Files.exists(subs) && Files.exists(lines)) {

                    copyFile(sections, workdir.resolve(SECTIONS_CSV));
                    copyFile(track, workdir.resolve(TRACK_CSV));
                    copyFile(nodes, workdir.resolve(NODES_CSV));
                    copyFile(subs, workdir.resolve(SUBSTATIONS_CSV));
                    copyFile(lines, workdir.resolve(LINES_CSV));

                } else {
                    throw new IllegalStateException(
                            "Scenario has application.conf but network CSVs are missing and " +
                                    "generateNetworkCsvsFromAppConf() is not implemented. " +
                                    "Provide sections.csv..lines.csv or implement generator. Scenario=" + scenarioId,
                            ex
                    );
                }
            }

        } else {
            // CSV network must exist, copy 2..6 to workdir
            requireExists(scenarioDir.resolve(SECTIONS_CSV));
            requireExists(scenarioDir.resolve(TRACK_CSV));
            requireExists(scenarioDir.resolve(NODES_CSV));
            requireExists(scenarioDir.resolve(SUBSTATIONS_CSV));
            requireExists(scenarioDir.resolve(LINES_CSV));

            copyFile(scenarioDir.resolve(SECTIONS_CSV), workdir.resolve(SECTIONS_CSV));
            copyFile(scenarioDir.resolve(TRACK_CSV), workdir.resolve(TRACK_CSV));
            copyFile(scenarioDir.resolve(NODES_CSV), workdir.resolve(NODES_CSV));
            copyFile(scenarioDir.resolve(SUBSTATIONS_CSV), workdir.resolve(SUBSTATIONS_CSV));
            copyFile(scenarioDir.resolve(LINES_CSV), workdir.resolve(LINES_CSV));
        }

        // runs
        if (runsMode == RunsMode.EXISTING_RUN_CSV) {
            copyFile(scenarioDir.resolve(RUN_CSV), workdir.resolve(RUN_CSV));
        } else {
            factory.generateRunCsv(workdir.resolve(RUN_CSV));
        }

        return new ScenarioWorkdir(
                scenarioId,
                workdir,
                scenarioMode,
                runsMode,
                appConfPath,
                workdir.resolve(SECTIONS_CSV),
                workdir.resolve(TRACK_CSV),
                workdir.resolve(NODES_CSV),
                workdir.resolve(SUBSTATIONS_CSV),
                workdir.resolve(LINES_CSV),
                workdir.resolve(RUN_CSV)
        );
    }

    private static void requireExists(Path p) {
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("Missing required file: " + p);
        }
    }

    private static Path copyFile(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        return Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
}
