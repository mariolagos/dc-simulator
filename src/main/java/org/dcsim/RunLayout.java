package org.dcsim;

import java.nio.file.Path;

public final class RunLayout {
    private final String projectId;
    private final String scenarioId;
    private final Path configFile;
    private final Path inputDir;
    private final Path outputRoot;
    private final Path exportDir;
    private final Path resultsDir;

    public RunLayout(
            String projectId,
            String scenarioId,
            Path configFile,
            Path inputDir,
            Path outputRoot
    ) {
        this.projectId = projectId;
        this.scenarioId = scenarioId;
        this.configFile = configFile;
        this.inputDir = inputDir;
        this.outputRoot = outputRoot;
        this.exportDir = outputRoot.resolve("exports").normalize();
        this.resultsDir = outputRoot.resolve("results").normalize();
    }

    public String projectId() {
        return projectId;
    }

    public String scenarioId() {
        return scenarioId;
    }

    public Path configFile() {
        return configFile;
    }

    public Path inputDir() {
        return inputDir;
    }

    public Path outputRoot() {
        return outputRoot;
    }

    public Path exportDir() {
        return exportDir;
    }

    public Path resultsDir() {
        return resultsDir;
    }

    @Override
    public String toString() {
        return "RunLayout{" +
                "projectId='" + projectId + '\'' +
                ", scenarioId='" + scenarioId + '\'' +
                ", configFile=" + configFile +
                ", inputDir=" + inputDir +
                ", outputRoot=" + outputRoot +
                ", exportDir=" + exportDir +
                ", resultsDir=" + resultsDir +
                '}';
    }
}