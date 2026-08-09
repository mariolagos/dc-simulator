package org.supply.app;

import com.typesafe.config.Config;
import org.supply.domain.RunCsvInput;
import org.supply.domain.RunSample;
import org.supply.domain.SystemParameters;
import org.supply.io.export.RunCsvFromExcel;
import org.supply.loader.GridModelLoader;
import org.supply.loader.RunCsvInputFactory;
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
import org.supply.solver.io.LongTableWriter;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;
import org.supply.track.TrackTransformService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

            solveRun(
                    solverContext.runInput(),
                    solverContext.systemParameters(),
                    baseNetwork,
                    writer
            );
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

        RunCsvInput runInput =
                new RunCsvInputFactory().build(
                        dcsim,
                        context.confFile()
                );

        SystemParameters systemParameters =
                new SystemParametersFactory().build(dcsim);

        TrackTransformService trackTransform =
                new DefaultTrackTransformService(trackModel);

        return new SolverContext(
                dcsim,
                grid,
                runInput,
                systemParameters,
                trackTransform
        );
    }

    private static void solveRun(
            RunCsvInput runInput,
            SystemParameters systemParameters,
            CalculationNetwork baseNetwork,
            LongTableWriter writer
    ) throws Exception {
        List<RunSample> samples = runSamples(runInput);

        TrainPositionFactory trainPositionFactory = new TrainPositionFactory();
        TrainNodeInserter trainNodeInserter =
                new TrainNodeInserter(systemParameters);

        for (RunSample sample : samples) {

            solveTimestep(baseNetwork, sample, trainPositionFactory, trainNodeInserter, writer);
        }
    }

    private static void solveTimestep(CalculationNetwork baseNetwork, RunSample sample, TrainPositionFactory trainPositionFactory, TrainNodeInserter trainNodeInserter, LongTableWriter writerwriter) {
        List<RunSample> timestepSamples = List.of(sample);

        List<CalculationTrainPosition> trainPositions =
                trainPositionFactory.fromRunSamples(timestepSamples);

        CalculationNetwork timestepNetwork =
                trainNodeInserter.insertTrainNodes(
                        baseNetwork,
                        trainPositions
                );

        if (DEBUG_TOPOLOGY) {
            TopologyPrinter.print(timestepNetwork);
        }

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        timestepNetwork,
                        "R1"
                );

        if (DEBUG_MATRIX) {
            MatrixPrinter.printSystem(
                    "DcSolver",
                    system,
                    20,
                    20,
                    6
            );
        }

        Map<String, Real> voltages =
                new LinearSystemSolver().solveVoltages(system);

        printSummary(
                timestepSamples,
                trainPositions,
                timestepNetwork,
                voltages
        );

        if (DEBUG_ALL_NODE_VOLTAGES) {
            printAllNodeVoltages(voltages);
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

    private static List<RunSample> runSamples(
            RunCsvInput runInput
    ) throws Exception {

        Path runExcel = runInput.runExcels().get(0);
        String trainId = runInput.trainIds().get(0);
        int departureTime = runInput.departureTimes().get(0);

        List<Map<String, String>> rows =
                RunCsvFromExcel.readFullRunRows(
                        runExcel,
                        trainId,
                        departureTime
                );

        List<RunSample> samples = new ArrayList<>();

        for (Map<String, String> row : rows) {
            samples.add(new RunSample(
                    Double.parseDouble(row.get("time_s")),
                    row.get("train_id"),
                    row.get("track"),
                    "SINGLE",
                    Double.parseDouble(row.get("position_m")),
                    Double.parseDouble(row.get("p_req_W"))
            ));
        }

        return samples;
    }
    private static List<RunSample> firstTimestepRunSamples(RunCsvInput runInput) throws Exception {
        Path runExcel = runInput.runExcels().get(0);
        String trainId = runInput.trainIds().get(0);
        int departureTime = runInput.departureTimes().get(0);

        List<Map<String, String>> rows =
                RunCsvFromExcel.readFullRunRows(runExcel, trainId, departureTime);

        Map<String, String> first = rows.get(0);

        return List.of(new RunSample(
                Double.parseDouble(first.get("time_s")),
                first.get("train_id"),
                first.get("track"),      // sectionId tills vidare om run.csv säger track
                "SINGLE",                // trackId temporärt
                Double.parseDouble(first.get("position_m")),
                Double.parseDouble(first.get("p_req_W"))
        ));
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
            RunCsvInput runInput,
            SystemParameters systemParameters,
            TrackTransformService trackTransform
    ) {}
}
