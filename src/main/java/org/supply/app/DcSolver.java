package org.supply.app;

import com.typesafe.config.Config;
import org.supply.domain.RunCsvInput;
import org.supply.domain.RunSample;
import org.supply.domain.SystemParameters;
import org.supply.io.export.RunCsvFromExcel;
import org.supply.loader.DcSimConfigLoader;
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
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.solver.model.ElectricalElement;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;
import org.supply.track.TrackTransformService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DcSolver {

    private static final boolean DEBUG_TOPOLOGY = false;
    private static final boolean DEBUG_MATRIX = false;
    private static final boolean DEBUG_ALL_NODE_VOLTAGES = false;

    public static void main(String[] args) throws Exception {
        run(args);
    }

    public static void run(String[] args) throws Exception {

        Path confFile = ExecutionLayoutFactory.resolveConfArg(args[0]);

        Config scenario = DcSimConfigLoader.loadScenarioConfig(confFile);
        Config dcsim = DcSimConfigLoader.requireDcsim(scenario, confFile);

        GridModel grid = new GridModelLoader().load(dcsim);
        LoadedTrackModel trackModel = new TrackConfigLoader().load(dcsim);

        RunCsvInput runInput = new RunCsvInputFactory().build(dcsim, confFile);
        SystemParameters systemParameters = new SystemParametersFactory().build(dcsim);

        TrackTransformService trackTransform = new DefaultTrackTransformService(trackModel);

        CalculationNetwork baseNetwork = new CalculationNetworkBuilder(trackTransform).buildBase(grid);

        List<RunSample> samples = firstTimestepRunSamples(runInput);
        List<CalculationTrainPosition> trainPositions = new TrainPositionFactory().fromRunSamples(samples);

        CalculationNetwork timestepNetwork = new TrainNodeInserter(systemParameters).insertTrainNodes(baseNetwork, trainPositions);

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

        printSummary(samples, trainPositions, timestepNetwork, voltages);

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

    private static List<CalculationTrainPosition> firstTimestepTrainPositions(
            RunCsvInput runInput,
            TrackTransformService trackTransform
    ) throws Exception {
        Path runExcel = runInput.runExcels().get(0);
        String trainId = runInput.trainIds().get(0);
        int departureTime = runInput.departureTimes().get(0);

        List<Map<String, String>> rows =
                RunCsvFromExcel.readFullRunRows(runExcel, trainId, departureTime);

        Map<String, String> first = rows.get(0);

        double positionM = Double.parseDouble(first.get("position_m"));
        Real pReqW  = Real.fromDouble(Double.parseDouble(first.get("p_Req_W")));

        return List.of(new CalculationTrainPosition(
                trainId,
                "1",
                "SINGLE",
                positionM,
                pReqW
        ));
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
}
