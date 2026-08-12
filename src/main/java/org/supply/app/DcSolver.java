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
import org.supply.solver.electrical.AdmittanceSystem;
import org.supply.solver.electrical.AdmittanceSystemBuilder;
import org.supply.solver.electrical.LinearSystemSolver;
import org.supply.solver.electrical.MatrixPrinter;
import org.supply.solver.electrical.SingleTimestepSolver;
import org.supply.solver.io.LongTableWriter;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.solver.model.ElectricalElement;
import org.supply.solver.optimization.PowerAllocationOptimizer;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;
import org.supply.track.TrackTransformService;
import org.supply.solver.model.CalculationTrainLoad;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DcSolver {

    private static final boolean DEBUG_TOPOLOGY = false;
    private static final boolean DEBUG_MATRIX = false;
    private static final boolean DEBUG_ALL_NODE_VOLTAGES = false;

    public static void main(String[] args) throws Exception {
        DcStudyContext context = DcStudyContextLoader.load(args[0]);
        run(context);
    }

    public static void run(DcStudyContext context) throws Exception {

        SolverContext solverContext =
                loadContext(context);

        try (LongTableWriter writer = createLongTableWriter(context)) {

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
                    writer
            );
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
            LongTableWriter writer
    ) throws Exception {

        Path runCsv = Path.of("dc", "exports", "run.csv");

        List<RunSample> samples =
                new RunSampleLoader().load(runCsv);

        TrainPositionFactory trainPositionFactory =
                new TrainPositionFactory();

        TrainNodeInserter trainNodeInserter =
                new TrainNodeInserter(systemParameters);

        for (RunSample sample : samples) {
            solveTimestep(
                    systemParameters,
                    baseNetwork,
                    sample,
                    trainPositionFactory,
                    trainNodeInserter,
                    writer
            );
        }
    }

    private static void solveTimestep(
            SystemParameters systemParameters,
            CalculationNetwork baseNetwork,
            RunSample sample,
            TrainPositionFactory trainPositionFactory,
            TrainNodeInserter trainNodeInserter,
            LongTableWriter writer
    ) {
        List<RunSample> timestepSamples = List.of(sample);

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

        SingleTimestepSolver timestepSolver =
                new SingleTimestepSolver(
                        systemParameters,
                        new LinearSystemSolver()
                );

        SingleTimestepSolver.NetworkResult solveResult =
                timestepSolver.solve(
                        timestepNetwork,
                        "R1",
                        requestedPowersW,
                        200,
                        1e-3
                );

        if (!solveResult.converged()) {

            PowerAllocationOptimizer optimizer =
                    new PowerAllocationOptimizer();

            double[] requested =
                    timestepNetwork.trainLoads().stream()
                            .mapToDouble(load -> load.pReqW().asDouble())
                            .toArray();

            double[] lowerBounds =
                    Arrays.stream(requested)
                            .map(p -> p < 0.0 ? p : 0.0)
                            .toArray();

            double[] upperBounds =
                    Arrays.stream(requested)
                            .map(p -> p < 0.0 ? 0.0 : p)
                            .toArray();

            // ... = här kommer evaluator + optimize-anrop + ny solve
        }
        Map<String, Real> voltages =
                solveResult.voltages();

        if (DEBUG_TOPOLOGY) {
            TopologyPrinter.print(timestepNetwork);
        }

        printSummary(
                timestepSamples,
                trainPositions,
                timestepNetwork,
                voltages
        );

        saveResults(
                timestepSamples,
                trainPositions,
                timestepNetwork,
                voltages,
                writer
        );

        if (DEBUG_ALL_NODE_VOLTAGES) {
            printAllNodeVoltages(voltages);
        }
    }

    private static void saveResults(List<RunSample> timestepSamples, List<CalculationTrainPosition> trainPositions, CalculationNetwork timestepNetwork, Map<String, Real> voltages, LongTableWriter writer) {
        double timeSec = timestepSamples.get(0).timeS();

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


    private static void printSummary(
            List<RunSample> samples,
            List<CalculationTrainPosition> trainPositions,
            CalculationNetwork timestepNetwork,
            Map<String, Real> voltages
    ) {
        double timeSec = samples.isEmpty() ? Double.NaN : samples.get(0).timeS();

        System.out.println("=== DcSolver ===");
        System.out.printf(
                "t=%.3f s  trains=%d  nodes=%d  branches=%d  trainLoads=%d%n",
                timeSec,
                trainPositions.size(),
                timestepNetwork.nodes().size(),
                timestepNetwork.branches().size(),
                timestepNetwork.trainLoads().size()
        );

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

            System.out.printf(
                    "%s  pos=%.1f m  P_req=%.0f W  U=%.3f V  terminals=%s/%s%n",
                    train.trainId(),
                    train.positionM(),
                    train.pReqW().asDouble(),
                    trainVoltageV,
                    feedingNodeId,
                    returnNodeId
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
                context.dcsim().getString("hash")
        );
    }

    private record SolverContext(
            Config dcsim,
            GridModel grid,
            SystemParameters systemParameters,
            TrackTransformService trackTransform
    ) {
    }

}
