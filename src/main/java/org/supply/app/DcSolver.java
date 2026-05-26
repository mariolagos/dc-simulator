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
import org.supply.solver.build.TrainNodeInserter;
import org.supply.solver.build.TrainPositionFactory;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;
import org.supply.track.TrackTransformService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DcSolver {

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

        CalculationNetwork timestepNetwork = new TrainNodeInserter().insertTrainNodes(baseNetwork, trainPositions);

        System.out.println("DcSolver startup OK");
        System.out.println("base nodes=" + baseNetwork.nodes().size());
        System.out.println("base branches=" + baseNetwork.branches().size());
        System.out.println("active trains=" + trainPositions.size());
        System.out.println("timestep nodes=" + timestepNetwork.nodes().size());
        System.out.println("timestep branches=" + timestepNetwork.branches().size());

        System.out.println("train loads=" + timestepNetwork.trainLoads().size());

        for (CalculationNode n : timestepNetwork.nodes()) {
            System.out.println(
                    "node "
                            + n.id()
                            + " section=" + n.sectionId()
                            + " track=" + n.trackId()
                            + " pos=" + n.positionM()
                            + " type=" + n.type()
            );
        }

        for (CalculationTrainPosition p : trainPositions) {
            System.out.println(
                    "train "
                            + p.trainId()
                            + " section=" + p.sectionId()
                            + " track=" + p.trackId()
                            + " positionM=" + p.positionM()
            );
        }

        for (CalculationBranch b : timestepNetwork.branches()) {
            System.out.println(
                    "branch "
                            + b.id()
                            + " "
                            + b.fromNodeId()
                            + " -> "
                            + b.toNodeId()
                            + " R="
                            + b.resistanceOhm()
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
