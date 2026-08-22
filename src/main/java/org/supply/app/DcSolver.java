package org.supply.app;

import com.typesafe.config.Config;
import org.supply.domain.RunSample;
import org.supply.domain.SystemParameters;
import org.supply.loader.GridModelLoader;
import org.supply.loader.RunSampleLoader;
import org.supply.loader.SystemParametersFactory;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.build.CalculationNetworkBuilder;
import org.supply.solver.build.TopologyPrinter;
import org.supply.solver.build.TrainNodeInserter;
import org.supply.solver.build.TrainPositionFactory;
import org.supply.solver.electrical.LinearSystemSolver;
import org.supply.solver.electrical.SingleTimestepSolver;
import org.supply.solver.io.LongTableWriter;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.solver.model.DiodeSubstationElement;
import org.supply.solver.model.ElectricalElement;
import org.supply.solver.model.TrainLoadElement;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;
import org.supply.track.TrackTransformService;
import org.supply.solver.model.CalculationTrainLoad;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DcSolver {

    private static final boolean DEBUG_TOPOLOGY = false;
    private static final boolean DEBUG_MATRIX = false;
    private static final boolean DEBUG_ALL_NODE_VOLTAGES = false;

    private record SolveResult(
            SingleTimestepSolver.NetworkResult networkResult,
            Map<String, Double> allocatedPowersW
    ) {
    }

    public static void main(String[] args) throws Exception {
        DcStudyContext context = DcStudyContextLoader.load(args[0]);
        run(context);
    }

    public static void run(DcStudyContext context) throws Exception {

        SolverContext solverContext =
                loadContext(context);

        try (LongTableWriter writer = createLongTableWriter(context)) {

            Path runCsv =
                    context.exportDirectory()
                            .resolve("run.csv");

            CalculationNetwork baseNetwork =
                    new CalculationNetworkBuilder(
                            solverContext.trackTransform()
                    ).buildBase(
                            solverContext.grid()
                    );

            saveStaticResults(
                    solverContext.systemParameters,
                    baseNetwork,
                    writer);

            solveRun(
                    solverContext.systemParameters(),
                    baseNetwork,
                    writer,
                    runCsv);
        }
    }

    private static void saveStaticResults(SystemParameters systemParameters, CalculationNetwork baseNetwork, LongTableWriter writer) {
        systemParameters.saveStaticData(writer);
        for (ElectricalElement element : baseNetwork.elements()) {
            element.saveStaticResult(writer);
        }
    }

    private static SolverContext loadContext(
            DcStudyContext context
    ) throws Exception {

        Config dcsim = context.dcsim();

        GridModel grid =
                new GridModelLoader().load(dcsim);

        LoadedTrackModel trackModel =
                new TrackConfigLoader().load(dcsim);

        SystemParameters systemParameters =
                new SystemParametersFactory().build(dcsim);

        TrackTransformService trackTransform =
                new DefaultTrackTransformService(trackModel);

        return new SolverContext(
                dcsim,
                grid,
                systemParameters,
                trackTransform
        );
    }

    private static void solveRun(
            SystemParameters systemParameters,
            CalculationNetwork baseNetwork,
            LongTableWriter writer,
            Path runCsv) throws Exception {

        List<RunSample> samples =
                new RunSampleLoader().load(runCsv);

        TrainPositionFactory trainPositionFactory =
                new TrainPositionFactory();

        TrainNodeInserter trainNodeInserter =
                new TrainNodeInserter(systemParameters);

        Map<Double, List<RunSample>> samplesByTime =
                samples.stream()
                        .collect(Collectors.groupingBy(
                                RunSample::timeS,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        Map<String, Real> previousVoltages = Map.of();

        for (List<RunSample> timestepSamples : samplesByTime.values()) {

            SingleTimestepSolver.NetworkResult result =
                    solveTimestep(
                            systemParameters,
                            baseNetwork,
                            timestepSamples,
                            trainPositionFactory,
                            trainNodeInserter,
                            writer,
                            previousVoltages
                    );

            previousVoltages = result.voltages();
        }
    }

    private static SingleTimestepSolver.NetworkResult solveTimestep(
            SystemParameters systemParameters,
            CalculationNetwork baseNetwork,
            List<RunSample> timestepSamples,
            TrainPositionFactory trainPositionFactory,
            TrainNodeInserter trainNodeInserter,
            LongTableWriter writer,
            Map<String, Real> previousVoltages
    ) {

        List<CalculationTrainPosition> trainPositions =
                trainPositionFactory.fromRunSamples(timestepSamples);

        CalculationNetwork timestepNetwork =
                trainNodeInserter.insertTrainNodes(
                        baseNetwork,
                        trainPositions
                );

        Map<String, Double> requestedPowersW =
                timestepNetwork.trainLoads().stream()
                        .collect(Collectors.toMap(
                                CalculationTrainLoad::trainId,
                                load -> load.pReqW().asDouble()
                        ));


        if (DEBUG_TOPOLOGY) {
            TopologyPrinter.print(timestepNetwork);
        }


        SolveResult solveResult =
                solveWithFallback(
                        systemParameters,
                        timestepNetwork,
                        requestedPowersW,
                        previousVoltages
                );

        Map<String, Real> voltages =
                solveResult.networkResult().voltages();

        saveResults(
                systemParameters,
                timestepSamples,
                trainPositions,
                timestepNetwork,
                voltages,
                solveResult.allocatedPowersW(),
                writer
        );

        if (DEBUG_ALL_NODE_VOLTAGES) {
            printAllNodeVoltages(voltages);
        }

        return solveResult.networkResult();
    }

    private static void saveResults(
            SystemParameters systemParameters,
            List<RunSample> timestepSamples,
            List<CalculationTrainPosition> trainPositions,
            CalculationNetwork timestepNetwork,
            Map<String, Real> voltages,
            Map<String, Double> allocatedPowersW,
            LongTableWriter writer
    ) {
        double timeSec = timestepSamples.get(0).timeS();

        saveTrainPositions(
                trainPositions,
                timestepNetwork,
                voltages,
                writer,
                timeSec
        );

        saveNetworkResults(
                systemParameters,
                timestepNetwork,
                voltages,
                allocatedPowersW,
                writer,
                timeSec
        );
    }
    private static void saveNetworkResults(
            SystemParameters systemParameters,
            CalculationNetwork timestepNetwork,
            Map<String, Real> voltages,
            Map<String, Double> allocatedPowersW,
            LongTableWriter writer,
            double timeSec
    ) {
        for (ElectricalElement element : timestepNetwork.elements()) {

            if (element instanceof DiodeSubstationElement dse) {
                saveSubstationResults(
                        voltages,
                        writer,
                        timeSec,
                        dse
                );
            }

            if (element instanceof TrainLoadElement trainLoad) {
                saveTrainResult(
                        systemParameters,
                        trainLoad,
                        voltages,
                        allocatedPowersW,
                        writer,
                        timeSec
                );
            }
        }
    }

    private static void saveTrainResult(
            SystemParameters systemParameters,
            TrainLoadElement trainLoad,
            Map<String, Real> voltages,
            Map<String, Double> allocatedPowersW,
            LongTableWriter writer,
            double timeSec
    ) {
        double feedingVoltageV =
                voltages.get(trainLoad.feedingNodeId()).asDouble();

        double returnVoltageV =
                voltages.get(trainLoad.returnNodeId()).asDouble();

        double terminalVoltageV =
                feedingVoltageV - returnVoltageV;

        double allocatedPowerW =
                allocatedPowersW.getOrDefault(
                        trainLoad.trainId(),
                        0.0
                );

        TrainLoadElement solvedLoad =
                new TrainLoadElement(
                        trainLoad.trainId(),
                        trainLoad.feedingNodeId(),
                        trainLoad.returnNodeId(),
                        allocatedPowerW,
                        terminalVoltageV,
                        systemParameters
                );

        double currentA =
                solvedLoad.currentA();

        double powerW =
                terminalVoltageV * currentA;

        writer.signalRow(
                timeSec,
                "TRAIN",
                trainLoad.trainId(),
                "i_A",
                currentA,
                "A",
                "RESULT",
                null,
                ""
        );

        writer.signalRow(
                timeSec,
                "TRAIN",
                trainLoad.trainId(),
                "p_W",
                powerW,
                "W",
                "RESULT",
                null,
                ""
        );
    }

    private static void saveSubstationResults(Map<String, Real> voltages, LongTableWriter writer, double timeSec, DiodeSubstationElement dse) {
        double feedingVoltageV =
                voltages.get(dse.feedingNodeId()).asDouble();

        double returnVoltageV =
                voltages.get(dse.returnNodeId()).asDouble();

        double terminalVoltageV =
                feedingVoltageV - returnVoltageV;

        boolean conducting =
                dse.enabled()
                        && terminalVoltageV <= dse.emfV().asDouble();

        double currentA =
                conducting
                        ? (dse.emfV().asDouble() - terminalVoltageV)
                        / dse.internalResistanceOhm().asDouble()
                        : 0.0;

        double powerW =
                terminalVoltageV * currentA;

        String state =
                !dse.enabled()
                        ? "DISABLED"
                        : conducting
                        ? "CONDUCTING"
                        : "BLOCKING";

        writer.signalRow(
                timeSec,
                "DIODE_SUBSTATION",
                dse.id(),
                "u_V",
                terminalVoltageV,
                "V",
                "RESULT",
                null,
                ""
        );

        writer.signalRow(
                timeSec,
                "DIODE_SUBSTATION",
                dse.id(),
                "i_A",
                currentA,
                "A",
                "RESULT",
                null,
                ""
        );

        writer.signalRow(
                timeSec,
                "DIODE_SUBSTATION",
                dse.id(),
                "p_W",
                powerW,
                "W",
                "RESULT",
                null,
                ""
        );

        writer.signalRow(
                timeSec,
                "DIODE_SUBSTATION",
                dse.id(),
                "state",
                state,
                "",
                "RESULT",
                null,
                ""
        );
    }

    private static void saveTrainPositions(List<CalculationTrainPosition> trainPositions, CalculationNetwork timestepNetwork, Map<String, Real> voltages, LongTableWriter writer, double timeSec) {
        for (CalculationTrainPosition train : trainPositions) {
            var load = timestepNetwork.trainLoads().stream()
                    .filter(x -> x.trainId().equals(train.trainId()))
                    .findFirst()
                    .orElse(null);

            String feedingNodeId = load == null ? null : load.feedingNodeId();
            String returnNodeId = load == null ? null : load.returnNodeId();

            double feedingV = voltageOf(voltages, feedingNodeId);
            double returnV = voltageOf(voltages, returnNodeId);
            double trainVoltageV = feedingV - returnV;

            writer.signalRow(
                    timeSec,
                    "TRAIN",
                    train.trainId(),
                    "position_m",
                    train.positionM(),
                    "m",
                    "INPUT",
                    null,
                    null
            );

            writer.signalRow(
                    timeSec,
                    "TRAIN",
                    train.trainId(),
                    "p_req_W",
                    train.pReqW().asDouble(),
                    "W",
                    "INPUT",
                    null,
                    null
            );

            writer.signalRow(
                    timeSec,
                    "TRAIN",
                    train.trainId(),
                    "u_V",
                    trainVoltageV,
                    "V",
                    "RESULT",
                    null,
                    null
            );

        }
    }

    private static double voltageOf(Map<String, Real> voltages, String nodeId) {
        if (nodeId == null) {
            return Double.NaN;
        }

        Real voltage = voltages.get(nodeId);
        return voltage == null ? Double.NaN : voltage.asDouble();
    }

    private static void printAllNodeVoltages(Map<String, Real> voltages) {
        System.out.println("=== Node voltages ===");

        for (Map.Entry<String, Real> e : voltages.entrySet()) {
            System.out.printf(
                    "%s = %.6f V%n",
                    e.getKey(),
                    e.getValue().asDouble()
            );
        }
    }

    private static LongTableWriter createLongTableWriter(
            DcStudyContext context
    ) throws Exception {

        Files.createDirectories(context.resultDirectory());

        return new LongTableWriter(
                context.resultDirectory()
                        .resolve("longtable.csv")
                        .toString(),
                true,
                "dc-simulator",
                context.studyId(),
                GitInfo.currentCommitHash()
        );
    }

    private record SolverContext(
            Config dcsim,
            GridModel grid,
            SystemParameters systemParameters,
            TrackTransformService trackTransform
    ) {
    }

    private static SolveResult solveWithFallback(
            SystemParameters systemParameters,
            CalculationNetwork timestepNetwork,
            Map<String, Double> requestedPowersW,
            Map<String, Real> previousVoltages
    ) {
        SingleTimestepSolver timestepSolver =
                new SingleTimestepSolver(
                        systemParameters,
                        new LinearSystemSolver()
                );

        SingleTimestepSolver.NetworkResult result =
                timestepSolver.solve(
                        timestepNetwork,
                        "R1",
                        requestedPowersW,
                        200,
                        1e-3,
                        previousVoltages
                );

        if (result.converged()) {
            return new SolveResult(
                    result,
                    requestedPowersW
            );
        }
        double feasibleAlpha = 0.0;
        double infeasibleAlpha = 1.0;

        SingleTimestepSolver.NetworkResult feasibleResult =
                timestepSolver.solve(
                        timestepNetwork,
                        "R1",
                        scaledPowers(requestedPowersW, 0.0),
                        200,
                        1e-3,
                        previousVoltages
                );

        if (!feasibleResult.converged()) {
            throw new IllegalStateException(
                    "DC network is not solvable even with zero train power"
            );
        }

        for (int i = 0; i < 30; i++) {
            double alpha =
                    0.5 * (feasibleAlpha + infeasibleAlpha);

            Map<String, Double> candidatePowersW =
                    scaledPowers(
                            requestedPowersW,
                            alpha
                    );

            SingleTimestepSolver.NetworkResult candidateResult =
                    timestepSolver.solve(
                            timestepNetwork,
                            "R1",
                            candidatePowersW,
                            200,
                            1e-3,
                            previousVoltages
                    );

            if (candidateResult.converged()) {
                feasibleAlpha = alpha;
                feasibleResult = candidateResult;
            } else {
                infeasibleAlpha = alpha;
            }
        }

        Map<String, Double> feasiblePowersW =
                scaledPowers(
                        requestedPowersW,
                        feasibleAlpha
                );

        return new SolveResult(
                feasibleResult,
                feasiblePowersW
        );
    }

    private static Map<String, Double> scaledPowers(
            Map<String, Double> requestedPowersW,
            double alpha
    ) {
        Map<String, Double> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry :
                requestedPowersW.entrySet()) {

            result.put(
                    entry.getKey(),
                    alpha * entry.getValue()
            );
        }

        return result;
    }
}
