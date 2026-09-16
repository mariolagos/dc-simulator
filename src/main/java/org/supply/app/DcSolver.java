package org.supply.app;

import com.typesafe.config.Config;
import org.supply.domain.Route;
import org.supply.domain.RunSample;
import org.supply.domain.SystemParameters;
import org.supply.loader.GridModelLoader;
import org.supply.loader.RouteFactory;
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
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.solver.model.DiodeSubstationElement;
import org.supply.solver.model.ElectricalElement;
import org.supply.solver.model.FixedLoadElement;
import org.supply.solver.model.TrainLoadElement;
import org.supply.solver.model.ThyristorSubstationElement;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;
import org.supply.track.TrackTransformService;
import org.supply.solver.model.CalculationTrainLoad;
import org.supply.solver.model.RegenerativeTrainElement;

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
    private static Object trackModel;

    record SolveResult(
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

            List<Route> routes =
                    new RouteFactory().build(
                            solverContext.dcsim().getConfig("traffic")
                    );

            solveRun(
                    solverContext.systemParameters(),
                    solverContext.trackTransform(),
                    baseNetwork,
                    writer,
                    runCsv,
                    routes,
                    resultIntervalSec(solverContext.dcsim())
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

        new TrackConfigLoader().load(
                dcsim,
                context.confFile()
        );

        LoadedTrackModel trackModel =
                new TrackConfigLoader().load(
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
                systemParameters,
                trackTransform
        );
    }

    private static void solveRun(
            SystemParameters systemParameters,
            TrackTransformService trackTransform,
            CalculationNetwork baseNetwork,
            LongTableWriter writer,
            Path runCsv,
            List<Route> routes,
            double resultIntervalSec
    ) throws Exception {

        List<RunSample> samples =
                new RunSampleLoader().load(runCsv);

        TrainPositionFactory trainPositionFactory =
                new TrainPositionFactory(trackTransform);

        TrainNodeInserter trainNodeInserter =
                new TrainNodeInserter(systemParameters, routes);

        Map<Double, List<RunSample>> samplesByTime =
                samples.stream()
                        .collect(Collectors.groupingBy(
                                RunSample::timeS,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        Map<String, Real> previousVoltages = Map.of();
        EnergyState energyState = new EnergyState();
        Double previousTimeSec = null;

        for (List<RunSample> timestepSamples : samplesByTime.values()) {

            double timeSec = timestepSamples.get(0).timeS();
            double intervalSec = previousTimeSec == null
                    ? resultIntervalSec
                    : Math.min(
                            Math.max(timeSec - previousTimeSec, 0.0),
                            resultIntervalSec
                    );

            SingleTimestepSolver.NetworkResult result =
                    solveTimestep(
                            systemParameters,
                            baseNetwork,
                            timestepSamples,
                            trainPositionFactory,
                            trainNodeInserter,
                            writer,
                            previousVoltages,
                            energyState,
                            intervalSec
                    );

            previousVoltages = result.voltages();
            previousTimeSec = timeSec;
        }
    }

    private static double resultIntervalSec(Config dcsim) {
        double tickDurationSec =
                dcsim.getDouble("simulationControl.tickDurationSec");

        if (dcsim.hasPath("export.exportResolution_s")) {
            double exportResolutionSec =
                    dcsim.getDouble("export.exportResolution_s");
            if (exportResolutionSec > 0.0) {
                return exportResolutionSec;
            }
        }

        return tickDurationSec;
    }

    private static boolean isAcceptable(
            SystemParameters systemParameters,
            CalculationNetwork network,
            SingleTimestepSolver.NetworkResult result
    ) {
        if (!result.converged()) {
            return false;
        }

        for (Real voltage : result.voltages().values()) {
            if (!Double.isFinite(voltage.asDouble())) {
                return false;
            }
        }

        double maxVoltageV =
                systemParameters.uMaxV() + 1e-3;

        for (CalculationTrainLoad load : network.trainLoads()) {
            Real feedingVoltage =
                    result.voltages().get(load.feedingNodeId());
            Real returnVoltage =
                    result.voltages().get(load.returnNodeId());

            if (feedingVoltage == null || returnVoltage == null) {
                return false;
            }

            double trainVoltageV =
                    feedingVoltage.asDouble()
                            - returnVoltage.asDouble();

            if (!Double.isFinite(trainVoltageV)
                    || trainVoltageV <= 0.0
                    || trainVoltageV > maxVoltageV) {
                return false;
            }
        }

        return true;
    }

    private static SingleTimestepSolver.NetworkResult solveTimestep(
            SystemParameters systemParameters,
            CalculationNetwork baseNetwork,
            List<RunSample> timestepSamples,
            TrainPositionFactory trainPositionFactory,
            TrainNodeInserter trainNodeInserter,
            LongTableWriter writer,
            Map<String, Real> previousVoltages,
            EnergyState energyState,
            double intervalSec
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
                writer,
                energyState,
                intervalSec
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
            LongTableWriter writer,
            EnergyState energyState,
            double intervalSec
    ) {
        double timeSec = timestepSamples.get(0).timeS();

        saveTrainPositions(
                trainPositions,
                timestepNetwork,
                voltages,
                writer,
                timeSec
        );

        PowerSnapshot powers = powerSnapshot(
                timestepNetwork,
                voltages,
                allocatedPowersW,
                systemParameters
        );

        accumulateEnergy(energyState, powers, intervalSec);

        saveNetworkResults(
                timestepNetwork,
                voltages,
                powers,
                energyState,
                writer,
                timeSec
        );

        saveLineResults(
                powers.lineLossesW(),
                energyState.lineLossesJ,
                writer,
                timeSec
        );

        saveSystemResults(powers, energyState, writer, timeSec);
    }

    private static void saveLineResults(
            Map<String, Double> lossesByLineW,
            Map<String, Double> energyByLineJ,
            LongTableWriter writer,
            double timeSec
    ) {
        for (Map.Entry<String, Double> entry : lossesByLineW.entrySet()) {
            String lineId = entry.getKey();

            writer.signalRow(
                    timeSec,
                    "LINE",
                    lineId,
                    "p_losses_W",
                    entry.getValue(),
                    "W",
                    "RESULT",
                    null,
                    ""
            );

            writer.signalRow(
                    timeSec,
                    "LINE",
                    lineId,
                    "e_losses_J",
                    energyByLineJ.get(lineId),
                    "J",
                    "RESULT",
                    null,
                    ""
            );
        }
    }

    static Map<String, Double> lineLossesW(
            CalculationNetwork network,
            Map<String, Real> voltages
    ) {
        Map<String, Double> result = new LinkedHashMap<>();

        for (CalculationBranch branch : network.branches()) {
            if (branch.sourceId().startsWith("internal_")) {
                continue;
            }

            Real fromVoltage = voltages.get(branch.fromNodeId());
            Real toVoltage = voltages.get(branch.toNodeId());
            if (fromVoltage == null || toVoltage == null) {
                throw new IllegalStateException(
                        "Missing voltage for line branch " + branch.id()
                );
            }

            double resistanceOhm = branch.resistanceOhm().asDouble();
            if (!(resistanceOhm > 0.0)) {
                throw new IllegalArgumentException(
                        "Branch resistance must be positive: " + branch.id()
                );
            }

            double voltageDifferenceV =
                    fromVoltage.asDouble() - toVoltage.asDouble();
            double lossW =
                    voltageDifferenceV * voltageDifferenceV
                            / resistanceOhm;

            result.merge(branch.sourceId(), lossW, Double::sum);
        }

        return result;
    }

    static void accumulateLineLossEnergyJ(
            Map<String, Double> energyByLineJ,
            Map<String, Double> lossesByLineW,
            double intervalSec
    ) {
        if (intervalSec < 0.0) {
            throw new IllegalArgumentException("intervalSec must be >= 0");
        }

        for (Map.Entry<String, Double> entry : lossesByLineW.entrySet()) {
            energyByLineJ.merge(
                    entry.getKey(),
                    entry.getValue() * intervalSec,
                    Double::sum
            );
        }
    }

    private static void saveNetworkResults(
            CalculationNetwork timestepNetwork,
            Map<String, Real> voltages,
            PowerSnapshot powers,
            EnergyState energyState,
            LongTableWriter writer,
            double timeSec
    ) {
        for (ElectricalElement element : timestepNetwork.elements()) {

            if (element instanceof DiodeSubstationElement dse) {
                saveSubstationResults(
                        voltages,
                        powers.substationPowersW().get(dse.id()),
                        energyState.substationsSuppliedJ.get(dse.id()),
                        energyState.substationsAbsorbedJ.get(dse.id()),
                        energyState.substationsNetJ.get(dse.id()),
                        writer,
                        timeSec,
                        dse
                );
            }

            if (element instanceof ThyristorSubstationElement tse) {
                saveThyristorSubstationResults(
                        voltages,
                        powers.substationPowersW().get(tse.id()),
                        energyState.substationsSuppliedJ.get(tse.id()),
                        energyState.substationsAbsorbedJ.get(tse.id()),
                        energyState.substationsNetJ.get(tse.id()),
                        writer,
                        timeSec,
                        tse
                );
            }

            if (element instanceof FixedLoadElement fixedLoad) {
                saveFixedLoadResults(
                        powers.fixedLoadPowersW().get(fixedLoad.id()),
                        energyState.fixedLoadsConsumedJ.get(fixedLoad.id()),
                        writer,
                        timeSec,
                        fixedLoad
                );
            }

            if (element instanceof TrainLoadElement trainLoad) {
                saveTrainResult(
                        trainLoad,
                        voltages,
                        powers.trainPowersW().get(trainLoad.trainId()),
                        energyState.trainsConsumedJ.get(trainLoad.trainId()),
                        energyState.trainsRegeneratedJ.get(trainLoad.trainId()),
                        energyState.trainsNetJ.get(trainLoad.trainId()),
                        writer,
                        timeSec
                );
            }
        }
    }

    private static void saveTrainResult(
            TrainLoadElement trainLoad,
            Map<String, Real> voltages,
            double powerW,
            Double consumedEnergyJ,
            Double regeneratedEnergyJ,
            Double netEnergyJ,
            LongTableWriter writer,
            double timeSec
    ) {
        double feedingVoltageV =
                voltages.get(trainLoad.feedingNodeId()).asDouble();

        double returnVoltageV =
                voltages.get(trainLoad.returnNodeId()).asDouble();

        double terminalVoltageV =
                feedingVoltageV - returnVoltageV;

        double currentA = powerW / terminalVoltageV;

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

        writeEnergySignal(writer, timeSec, "TRAIN", trainLoad.trainId(),
                "e_consumed_J", consumedEnergyJ);
        writeEnergySignal(writer, timeSec, "TRAIN", trainLoad.trainId(),
                "e_regenerated_J", regeneratedEnergyJ);
        writeEnergySignal(writer, timeSec, "TRAIN", trainLoad.trainId(),
                "e_net_J", netEnergyJ);

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

        writer.signalRow(
                timeSec,
                "TRAIN",
                trainLoad.trainId(),
                "p_delta_W",
                trainPowerDeltaW(trainLoad.requestedPowerW(), powerW),
                "W",
                "RESULT",
                null,
                ""
        );
    }

    static double trainPowerDeltaW(
            double requestedPowerW,
            double actualPowerW
    ) {
        return requestedPowerW - actualPowerW;
    }

    private static void saveSubstationResults(
            Map<String, Real> voltages,
            double powerW,
            Double suppliedEnergyJ,
            Double absorbedEnergyJ,
            Double netEnergyJ,
            LongTableWriter writer,
            double timeSec,
            DiodeSubstationElement dse
    ) {
        double feedingVoltageV =
                voltages.get(dse.feedingNodeId()).asDouble();

        double returnVoltageV =
                voltages.get(dse.returnNodeId()).asDouble();

        double terminalVoltageV =
                feedingVoltageV - returnVoltageV;

        boolean conducting =
                dse.enabled()
                        && terminalVoltageV <= dse.emfV().asDouble() + 1e-3;

        double currentA = powerW / terminalVoltageV;

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

        writeEnergySignal(writer, timeSec, "DIODE_SUBSTATION", dse.id(),
                "e_supplied_J", suppliedEnergyJ);
        writeEnergySignal(writer, timeSec, "DIODE_SUBSTATION", dse.id(),
                "e_absorbed_J", absorbedEnergyJ);
        writeEnergySignal(writer, timeSec, "DIODE_SUBSTATION", dse.id(),
                "e_net_J", netEnergyJ);

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

    private static PowerSnapshot powerSnapshot(
            CalculationNetwork network,
            Map<String, Real> voltages,
            Map<String, Double> allocatedPowersW,
            SystemParameters systemParameters
    ) {
        Map<String, Double> trainPowersW = new LinkedHashMap<>();
        Map<String, Double> substationPowersW = new LinkedHashMap<>();
        Map<String, Double> fixedLoadPowersW = new LinkedHashMap<>();

        for (ElectricalElement element : network.elements()) {
            if (element instanceof TrainLoadElement train) {
                double voltageV = terminalVoltageV(
                        voltages,
                        train.feedingNodeId(),
                        train.returnNodeId()
                );
                double allocatedPowerW = allocatedPowersW.getOrDefault(
                        train.trainId(),
                        0.0
                );
                double actualPowerW = voltageV * trainCurrentA(
                        systemParameters,
                        allocatedPowerW,
                        voltageV
                );
                trainPowersW.put(train.trainId(), actualPowerW);
            } else if (element instanceof DiodeSubstationElement substation) {
                double voltageV = terminalVoltageV(
                        voltages,
                        substation.feedingNodeId(),
                        substation.returnNodeId()
                );
                boolean conducting = substation.enabled()
                        && voltageV <= substation.emfV().asDouble() + 1e-3;
                double currentA = conducting
                        ? (substation.emfV().asDouble() - voltageV)
                        / substation.internalResistanceOhm().asDouble()
                        : 0.0;
                substationPowersW.put(substation.id(), voltageV * currentA);
            } else if (element instanceof ThyristorSubstationElement substation) {
                double voltageV = terminalVoltageV(
                        voltages,
                        substation.feedingNodeId(),
                        substation.returnNodeId()
                );
                double currentA = substation.enabled()
                        ? (substation.emfV().asDouble() - voltageV)
                        / substation.internalResistanceOhm().asDouble()
                        : 0.0;
                substationPowersW.put(substation.id(), voltageV * currentA);
            } else if (element instanceof FixedLoadElement fixedLoad) {
                fixedLoadPowersW.put(fixedLoad.id(), fixedLoad.powerW());
            }
        }

        Map<String, Double> lineLossesW = lineLossesW(network, voltages);
        double substationsW = sum(substationPowersW);
        double trainsW = sum(trainPowersW);
        double fixedLoadsW = sum(fixedLoadPowersW);
        double lossesW = sum(lineLossesW);

        return new PowerSnapshot(
                trainPowersW,
                substationPowersW,
                fixedLoadPowersW,
                lineLossesW,
                substationsW,
                trainsW,
                fixedLoadsW,
                lossesW,
                systemBalanceW(substationsW, trainsW, fixedLoadsW, lossesW)
        );
    }

    static double systemBalanceW(
            double substationsW,
            double trainsW,
            double fixedLoadsW,
            double lossesW
    ) {
        return substationsW - trainsW - fixedLoadsW - lossesW;
    }

    private static void accumulateEnergy(
            EnergyState energy,
            PowerSnapshot powers,
            double intervalSec
    ) {
        accumulateSplit(
                energy.trainsConsumedJ,
                energy.trainsRegeneratedJ,
                energy.trainsNetJ,
                powers.trainPowersW(),
                intervalSec
        );
        accumulateSplit(
                energy.substationsSuppliedJ,
                energy.substationsAbsorbedJ,
                energy.substationsNetJ,
                powers.substationPowersW(),
                intervalSec
        );
        accumulate(energy.fixedLoadsConsumedJ,
                powers.fixedLoadPowersW(), intervalSec);
        accumulateLineLossEnergyJ(
                energy.lineLossesJ,
                powers.lineLossesW(),
                intervalSec
        );

        energy.substationsSuppliedJTotal += consumedPowerW(
                powers.substationPowersW()) * intervalSec;
        energy.substationsAbsorbedJTotal += regeneratedPowerW(
                powers.substationPowersW()) * intervalSec;
        energy.substationsNetJTotal += powers.substationsW() * intervalSec;
        energy.trainsConsumedJTotal += consumedPowerW(
                powers.trainPowersW()) * intervalSec;
        energy.trainsRegeneratedJTotal += regeneratedPowerW(
                powers.trainPowersW()) * intervalSec;
        energy.trainsNetJTotal += powers.trainsW() * intervalSec;
        energy.fixedLoadsConsumedJTotal += powers.fixedLoadsW() * intervalSec;
        energy.lossesJTotal += powers.lossesW() * intervalSec;
        energy.balanceJTotal += powers.balanceW() * intervalSec;
    }

    private static void accumulate(
            Map<String, Double> energyJ,
            Map<String, Double> powerW,
            double intervalSec
    ) {
        for (Map.Entry<String, Double> entry : powerW.entrySet()) {
            energyJ.merge(
                    entry.getKey(),
                    entry.getValue() * intervalSec,
                    Double::sum
            );
        }
    }

    private static void accumulateSplit(
            Map<String, Double> consumedJ,
            Map<String, Double> regeneratedJ,
            Map<String, Double> netJ,
            Map<String, Double> powerW,
            double intervalSec
    ) {
        for (Map.Entry<String, Double> entry : powerW.entrySet()) {
            String id = entry.getKey();
            double power = entry.getValue();
            consumedJ.merge(id, positive(power) * intervalSec, Double::sum);
            regeneratedJ.merge(id, negativeMagnitude(power) * intervalSec, Double::sum);
            netJ.merge(id, power * intervalSec, Double::sum);
        }
    }

    private static double positive(double value) {
        return Math.max(value, 0.0);
    }

    private static double negativeMagnitude(double value) {
        return Math.max(-value, 0.0);
    }

    static double consumedPowerW(Map<String, Double> values) {
        return values.values().stream()
                .mapToDouble(DcSolver::positive)
                .sum();
    }

    static double regeneratedPowerW(Map<String, Double> values) {
        return values.values().stream()
                .mapToDouble(DcSolver::negativeMagnitude)
                .sum();
    }

    private static void saveSystemResults(
            PowerSnapshot powers,
            EnergyState energy,
            LongTableWriter writer,
            double timeSec
    ) {
        writePowerSignal(writer, timeSec, "p_substations_W", powers.substationsW());
        writePowerSignal(writer, timeSec, "p_trains_W", powers.trainsW());
        writePowerSignal(writer, timeSec, "p_fixed_loads_W", powers.fixedLoadsW());
        writePowerSignal(writer, timeSec, "p_losses_W", powers.lossesW());
        writePowerSignal(writer, timeSec, "p_balance_W", powers.balanceW());

        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_substations_supplied_J", energy.substationsSuppliedJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_substations_absorbed_J", energy.substationsAbsorbedJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_substations_net_J", energy.substationsNetJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_trains_consumed_J", energy.trainsConsumedJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_trains_regenerated_J", energy.trainsRegeneratedJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_trains_net_J", energy.trainsNetJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_fixed_loads_consumed_J", energy.fixedLoadsConsumedJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_losses_J", energy.lossesJTotal);
        writeEnergySignal(writer, timeSec, "SYSTEM", "DC",
                "e_balance_J", energy.balanceJTotal);
    }

    private static void writePowerSignal(
            LongTableWriter writer,
            double timeSec,
            String signal,
            double value
    ) {
        writer.signalRow(timeSec, "SYSTEM", "DC", signal, value,
                "W", "RESULT", null, "");
    }

    private static void writeEnergySignal(
            LongTableWriter writer,
            double timeSec,
            String objectType,
            String objectId,
            String signal,
            Double value
    ) {
        if (value != null) {
            writer.signalRow(timeSec, objectType, objectId, signal, value,
                    "J", "RESULT", null, "");
        }
    }

    private static void saveThyristorSubstationResults(
            Map<String, Real> voltages,
            double powerW,
            Double suppliedEnergyJ,
            Double absorbedEnergyJ,
            Double netEnergyJ,
            LongTableWriter writer,
            double timeSec,
            ThyristorSubstationElement substation
    ) {
        double voltageV = terminalVoltageV(
                voltages,
                substation.feedingNodeId(),
                substation.returnNodeId()
        );
        double currentA = powerW / voltageV;

        writer.signalRow(timeSec, "THYRISTOR_SUBSTATION", substation.id(),
                "u_V", voltageV, "V", "RESULT", null, "");
        writer.signalRow(timeSec, "THYRISTOR_SUBSTATION", substation.id(),
                "i_A", currentA, "A", "RESULT", null, "");
        writer.signalRow(timeSec, "THYRISTOR_SUBSTATION", substation.id(),
                "p_W", powerW, "W", "RESULT", null, "");
        writeEnergySignal(writer, timeSec, "THYRISTOR_SUBSTATION",
                substation.id(), "e_supplied_J", suppliedEnergyJ);
        writeEnergySignal(writer, timeSec, "THYRISTOR_SUBSTATION",
                substation.id(), "e_absorbed_J", absorbedEnergyJ);
        writeEnergySignal(writer, timeSec, "THYRISTOR_SUBSTATION",
                substation.id(), "e_net_J", netEnergyJ);
        writer.signalRow(timeSec, "THYRISTOR_SUBSTATION", substation.id(),
                "state", substation.enabled() ? "ENABLED" : "DISABLED",
                "", "RESULT", null, "");
    }

    private static void saveFixedLoadResults(
            double powerW,
            Double consumedEnergyJ,
            LongTableWriter writer,
            double timeSec,
            FixedLoadElement fixedLoad
    ) {
        writer.signalRow(timeSec, "FIXED_LOAD", fixedLoad.id(),
                "p_W", powerW, "W", "RESULT", null, "");
        writeEnergySignal(writer, timeSec, "FIXED_LOAD", fixedLoad.id(),
                "e_consumed_J", consumedEnergyJ);
    }

    private static double terminalVoltageV(
            Map<String, Real> voltages,
            String feedingNodeId,
            String returnNodeId
    ) {
        return voltages.get(feedingNodeId).asDouble()
                - voltages.get(returnNodeId).asDouble();
    }

    private static double sum(Map<String, Double> values) {
        return values.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private record PowerSnapshot(
            Map<String, Double> trainPowersW,
            Map<String, Double> substationPowersW,
            Map<String, Double> fixedLoadPowersW,
            Map<String, Double> lineLossesW,
            double substationsW,
            double trainsW,
            double fixedLoadsW,
            double lossesW,
            double balanceW
    ) {
    }

    private static final class EnergyState {
        private final Map<String, Double> trainsConsumedJ = new LinkedHashMap<>();
        private final Map<String, Double> trainsRegeneratedJ = new LinkedHashMap<>();
        private final Map<String, Double> trainsNetJ = new LinkedHashMap<>();
        private final Map<String, Double> substationsSuppliedJ = new LinkedHashMap<>();
        private final Map<String, Double> substationsAbsorbedJ = new LinkedHashMap<>();
        private final Map<String, Double> substationsNetJ = new LinkedHashMap<>();
        private final Map<String, Double> fixedLoadsConsumedJ = new LinkedHashMap<>();
        private final Map<String, Double> lineLossesJ = new LinkedHashMap<>();
        private double substationsSuppliedJTotal;
        private double substationsAbsorbedJTotal;
        private double substationsNetJTotal;
        private double trainsConsumedJTotal;
        private double trainsRegeneratedJTotal;
        private double trainsNetJTotal;
        private double fixedLoadsConsumedJTotal;
        private double lossesJTotal;
        private double balanceJTotal;
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
                    "electric_route_id",
                    train.routeId(),
                    "",
                    "INPUT",
                    null,
                    null
            );

            writer.signalRow(
                    timeSec,
                    "TRAIN",
                    train.trainId(),
                    "electric_route_position_m",
                    train.routePositionM(),
                    "m",
                    "INPUT",
                    null,
                    null
            );

            writer.signalRow(
                    timeSec,
                    "TRAIN",
                    train.trainId(),
                    "section_id",
                    train.sectionId(),
                    "",
                    "INPUT",
                    null,
                    null
            );

            writer.signalRow(
                    timeSec,
                    "TRAIN",
                    train.trainId(),
                    "track_id",
                    train.trackId(),
                    "",
                    "INPUT",
                    null,
                    null
            );

            writer.signalRow(
                    timeSec,
                    "TRAIN",
                    train.trainId(),
                    "position_m",
                    train.positionM(),
                    "",
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

    static double trainCurrentA(
            SystemParameters systemParameters,
            double allocatedPowerW,
            double terminalVoltageV
    ) {
        if (allocatedPowerW < 0.0) {
            return -RegenerativeTrainElement.regenerativeCurrentA(
                    allocatedPowerW,
                    terminalVoltageV,
                    850.0,
                    systemParameters.uMaxV()
            );
        }

        TrainLoadElement load =
                new TrainLoadElement(
                        "result",
                        "F",
                        "R",
                        allocatedPowerW,
                        terminalVoltageV,
                        systemParameters
                );

        return load.currentA();
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
                        .resolve(context.studyId() + "_longtable.csv")
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

    static SolveResult solveWithFallback(
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
                solveWithRetry(
                        systemParameters, timestepSolver,
                        timestepNetwork,
                        requestedPowersW,
                        previousVoltages
                );

        if (isAcceptable(
                systemParameters,
                timestepNetwork,
                result
        )) {
            return new SolveResult(
                    result,
                    requestedPowersW
            );
        }

        double feasibleAlpha = 0.0;
        double infeasibleAlpha = 1.0;

        SingleTimestepSolver.NetworkResult feasibleResult =
                solveWithRetry(
                        systemParameters, timestepSolver,
                        timestepNetwork,
                        scaledRegenerationOnly(
                                requestedPowersW,
                                0.0
                        ),
                        previousVoltages
                );

        if (!isAcceptable(
                systemParameters,
                timestepNetwork,
                feasibleResult
        )) {
            throw new IllegalStateException(
                    "DC network is not solvable even with zero regenerative train power"
            );
        }

        for (int i = 0; i < 30; i++) {
            double alpha =
                    0.5 * (feasibleAlpha + infeasibleAlpha);

            Map<String, Double> candidatePowersW =
                    scaledRegenerationOnly(
                            requestedPowersW,
                            alpha
                    );

            SingleTimestepSolver.NetworkResult candidateResult =
                    solveWithRetry(
                            systemParameters, timestepSolver,
                            timestepNetwork,
                            candidatePowersW,
                            previousVoltages
                    );

            if (isAcceptable(
                    systemParameters,
                    timestepNetwork,
                    candidateResult
            )) {
                feasibleAlpha = alpha;
                feasibleResult = candidateResult;
            } else {
                infeasibleAlpha = alpha;
            }
        }

        Map<String, Double> feasiblePowersW =
                scaledRegenerationOnly(
                        requestedPowersW,
                        feasibleAlpha
                );

        return new SolveResult(
                feasibleResult,
                feasiblePowersW
        );
    }

    private static SingleTimestepSolver.NetworkResult solveWithRetry(
            SystemParameters systemParameters, SingleTimestepSolver timestepSolver,
            CalculationNetwork timestepNetwork,
            Map<String, Double> powersW,
            Map<String, Real> previousVoltages
    ) {
        SingleTimestepSolver.NetworkResult result =
                timestepSolver.solve(
                        timestepNetwork,
                        systemParameters.referenceNodeId(),
                        powersW,
                        200,
                        1e-3,
                        previousVoltages
                );

        if (!result.converged() && !previousVoltages.isEmpty()) {
            result =
                    timestepSolver.solve(
                            timestepNetwork,
                            systemParameters.referenceNodeId(),
                            powersW,
                            200,
                            1e-3,
                            Map.of()
                    );
        }

        return result;
    }

    private static Map<String, Double> scaledRegenerationOnly(
            Map<String, Double> requestedPowersW,
            double alpha
    ) {
        Map<String, Double> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry :
                requestedPowersW.entrySet()) {

            double powerW = entry.getValue();

            result.put(
                    entry.getKey(),
                    powerW < 0.0
                            ? alpha * powerW
                            : powerW
            );
        }

        return result;
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
