package org.supply.app;

import com.typesafe.config.Config;

import java.nio.file.Path;

public final class DcStudyContext {

    private final Path confFile;
    private final Path workingDirectory;

    private final Config scenario;
    private final Config dcsim;

    private final String studyId;
    private final String studyName;
    private final String studyDescription;

    private final Path exportDirectory;
    private final Path resultDirectory;

    public DcStudyContext(
            Path confFile,
            Path workingDirectory,
            Config scenario,
            Config dcsim,
            String studyId,
            String studyName,
            String studyDescription,
            Path exportDirectory,
            Path resultDirectory
    ) {
        this.confFile = confFile;
        this.workingDirectory = workingDirectory;
        this.scenario = scenario;
        this.dcsim = dcsim;
        this.studyId = studyId;
        this.studyName = studyName;
        this.studyDescription = studyDescription;
        this.exportDirectory = exportDirectory;
        this.resultDirectory = resultDirectory;
    }

    public Path confFile() {
        return confFile;
    }

    public Path workingDirectory() {
        return workingDirectory;
    }

    public Config scenario() {
        return scenario;
    }

    public Config dcsim() {
        return dcsim;
    }

    public String studyId() {
        return studyId;
    }

    public String studyName() {
        return studyName;
    }

    public String studyDescription() {
        return studyDescription;
    }

    public Path exportDirectory() {
        return exportDirectory;
    }

    public Path resultDirectory() {
        return resultDirectory;
    }
}