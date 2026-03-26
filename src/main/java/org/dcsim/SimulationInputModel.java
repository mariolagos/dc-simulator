package org.dcsim;

import org.dcsim.actors.SimulationSpeed;
import org.dcsim.config.PowerProfiles;
import org.dcsim.electric.GridModel;
import org.dcsim.math.FieldElement;
import org.dcsim.power.PowerProfile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SimulationInputModel<F extends FieldElement<F>> {

    public record TrainSpawn(
            String trainId,
            PowerProfile profile,
            int departureSec,
            boolean sameModel,
            double auxKW) {
    }

    private final String projectId;
    private final String scenarioId;
    private final SimulationSpeed simulationSpeed;
    private final Path configFile;
    private final Path inputDir;
    private final Path outputRoot;
    private final Path exportDir;
    private final Path resultsDir;
    private final Path reportsDir;
    private final Path runCsv;

    private final double startSec;
    private final double endSec;
    private final double tickSec;
//    private final double speed;
    private final int stopAfterSteps;

    private final GridModel<F> gridModel;
    private final PowerProfiles powerProfiles;
    private final List<TrainSpawn> spawns;

    private final boolean exportOnly;

    private SimulationInputModel(Builder<F> b) {
        this.projectId = Objects.requireNonNull(b.projectId, "projectId");
        this.scenarioId = Objects.requireNonNull(b.scenarioId, "scenarioId");
        this.configFile = Objects.requireNonNull(b.configFile, "configFile");
        this.inputDir = Objects.requireNonNull(b.inputDir, "inputDir");
        this.outputRoot = Objects.requireNonNull(b.outputRoot, "outputRoot");
        this.exportDir = Objects.requireNonNull(b.exportDir, "exportDir");
        this.resultsDir = Objects.requireNonNull(b.resultsDir, "resultsDir");
        this.reportsDir = Objects.requireNonNull(b.reportsDir, "reportsDir");
        this.runCsv = Objects.requireNonNull(b.runCsv, "runCsv");
        this.simulationSpeed = Objects.requireNonNull(b.simulationSpeed, "simulationSpeed");

        this.startSec = b.startSec;
        this.endSec = b.endSec;
        this.tickSec = b.tickSec;
//        this.speed = b.speed;
        this.stopAfterSteps = b.stopAfterSteps;

        this.exportOnly = b.exportOnly;

        if (b.exportOnly) {
            this.gridModel = b.gridModel;
            this.powerProfiles = (b.powerProfiles != null) ? b.powerProfiles : new PowerProfiles();
        } else {
            this.gridModel = Objects.requireNonNull(b.gridModel, "gridModel");
            this.powerProfiles = Objects.requireNonNull(b.powerProfiles, "powerProfiles");
        }

        this.spawns = List.copyOf(b.spawns);    }

    public static <F extends FieldElement<F>> Builder<F> builder() {
        return new Builder<>();
    }

    public static final class Builder<F extends FieldElement<F>> {
        private String projectId;
        private String scenarioId;

        private Path configFile;
        private Path inputDir;
        private Path outputRoot;
        private Path exportDir;
        private Path resultsDir;
        private Path reportsDir;
        private Path runCsv;

        private double startSec;
        private double endSec;
        private double tickSec;
//        private double speed = 1.0;
        private int stopAfterSteps = -1;
        private SimulationSpeed simulationSpeed = SimulationSpeed.FAST;

        private GridModel<F> gridModel;
        private PowerProfiles powerProfiles;
        private List<TrainSpawn> spawns = new ArrayList<>();

        private boolean exportOnly;

        public Builder<F> projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder<F> scenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
            return this;
        }

        public Builder<F> configFile(Path configFile) {
            this.configFile = configFile;
            return this;
        }

        public Builder<F> inputDir(Path inputDir) {
            this.inputDir = inputDir;
            return this;
        }

        public Builder<F> outputRoot(Path outputRoot) {
            this.outputRoot = outputRoot;
            return this;
        }

        public Builder<F> exportDir(Path exportDir) {
            this.exportDir = exportDir;
            return this;
        }

        public Builder<F> resultsDir(Path resultsDir) {
            this.resultsDir = resultsDir;
            return this;
        }

        public Builder<F> reportsDir(Path reportsDir) {
            this.reportsDir = reportsDir;
            return this;
        }

        public Builder<F> runCsv(Path runCsv) {
            this.runCsv = runCsv;
            return this;
        }
        public Builder<F> startSec(double startSec) {
            this.startSec = startSec;
            return this;
        }

        public Builder<F> endSec(double endSec) {
            this.endSec = endSec;
            return this;
        }

        public Builder<F> tickSec(double tickSec) {
            this.tickSec = tickSec;
            return this;
        }

//        public Builder<F> speed(double speed) {
//            this.speed = speed;
//            return this;
//        }

        public Builder<F> stopAfterSteps(int stopAfterSteps) {
            this.stopAfterSteps = stopAfterSteps;
            return this;
        }

        public Builder<F> gridModel(GridModel<F> gridModel) {
            this.gridModel = gridModel;
            return this;
        }

        public Builder<F> powerProfiles(PowerProfiles powerProfiles) {
            this.powerProfiles = powerProfiles;
            return this;
        }

        public Builder<F> spawns(List<TrainSpawn> spawns) {
            this.spawns = (spawns == null) ? new ArrayList<>() : new ArrayList<>(spawns);
            return this;
        }
        public Builder<F> exportOnly(boolean exportOnly) {
            this.exportOnly = exportOnly;
            return this;
        }

        public SimulationInputModel<F> build() {
            this.runCsv = Objects.requireNonNull(runCsv, "runCsv");
            return new SimulationInputModel<>(this);
        }

        public Builder<F> simulationSpeed(SimulationSpeed simulationSpeed) {
            this.simulationSpeed = Objects.requireNonNull(simulationSpeed, "simulationSpeed");
            return this;
        }
    }

    public Path longTablePath() {
        return resultsDir.resolve("longtable.csv");
    }

    public Path electricalCsvPath(String scenarioFileName) {
        int dot = scenarioFileName.lastIndexOf('.');
        String base = (dot > 0) ? scenarioFileName.substring(0, dot) : scenarioFileName;
        return resultsDir.resolve("electrical_" + base + ".csv");
    }

    public String getProjectId() {
        return projectId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public Path getConfigFile() {
        return configFile;
    }

    public Path getInputDir() {
        return inputDir;
    }

    public Path getOutputRoot() {
        return outputRoot;
    }

    public Path getExportDir() {
        return exportDir;
    }

    public Path getResultsDir() {
        return resultsDir;
    }

    public Path getReportsDir() {
        return reportsDir;
    }

    public Path getRunCsv() {
        return runCsv;
    }

    public SimulationSpeed getSimulationSpeed() {
        return simulationSpeed;
    }

    public double getStartSec() {
        return startSec;
    }

    public double getEndSec() {
        return endSec;
    }

    public double getTickSec() {
        return tickSec;
    }

//    public double getSpeed() {
//        return speed;
//    }

    public int getStopAfterSteps() {
        return stopAfterSteps;
    }

    public GridModel<F> getGridModel() {
        return gridModel;
    }

    public PowerProfiles getPowerProfiles() {
        return powerProfiles;
    }

    public List<TrainSpawn> getSpawns() {
        return spawns;
    }

    public boolean isExportOnly() {
        return exportOnly;
    }

    public boolean shouldRunSolver() {
        return !exportOnly;
    }
}