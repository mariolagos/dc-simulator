package org.supply.app;

import com.typesafe.config.Config;
import org.supply.domain.RunCsvInput;
import org.supply.domain.SystemParameters;
import org.supply.io.export.RunCsvFromExcel;
import org.supply.loader.DcSimConfigLoader;
import org.supply.loader.GridModelLoader;
import org.supply.loader.RunCsvInputFactory;
import org.supply.loader.SystemParametersFactory;
import org.supply.model.GridModel;
import org.supply.solver.build.CalculationNetworkBuilder;
import org.supply.solver.build.TrainNodeInserter;
import org.supply.solver.model.CalculationNetwork;
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

        List<CalculationTrainPosition> trainPositions = firstTimestepTrainPositions(runInput, trackTransform);

        CalculationNetwork timestepNetwork = new TrainNodeInserter().insertTrainNodes(baseNetwork, trainPositions);

        System.out.println("DcSolver startup OK");
        System.out.println("base nodes=" + baseNetwork.nodes().size());
        System.out.println("base branches=" + baseNetwork.branches().size());
        System.out.println("active trains=" + trainPositions.size());
        System.out.println("timestep nodes=" + timestepNetwork.nodes().size());
        System.out.println("timestep branches=" + timestepNetwork.branches().size());
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

        return List.of(new CalculationTrainPosition(
                trainId,
                "1",
                "SINGLE",
                positionM
        ));
    }
}
