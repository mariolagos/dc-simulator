package org.supply.solver.electrical;

import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.model.*;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SingleTimestepSolver {

    private static final double RELAXATION = 0.1;
    private final SystemParameters systemParameters;
    private final LinearSystemSolver linearSystemSolver;

    public SingleTimestepSolver(SystemParameters systemParameters, LinearSystemSolver linearSystemSolver) {
        this.systemParameters = systemParameters;
        this.linearSystemSolver = linearSystemSolver;
    }

    public Result solveConstantPowerTrain(
            CalculationNetwork baseNetwork,
            String referenceNodeId,
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double initialVoltageV,
            int maxIterations,
            double toleranceV
    ) {
        double voltageGuessV = initialVoltageV;
        Map<String, Real> voltages = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            CalculationNetwork network = withTrainLoad(
                    baseNetwork,
                    feedingNodeId,
                    returnNodeId,
                    requestedPowerW,
                    voltageGuessV
            );

            AdmittanceSystem system =
                    new AdmittanceSystemBuilder().build(
                            network,
                            referenceNodeId
                    );

            voltages =
                    linearSystemSolver.solveVoltages(system);

            double newVoltageV =
                    voltageBetween(
                            voltages,
                            feedingNodeId,
                            returnNodeId
                    );

            if (Math.abs(newVoltageV - voltageGuessV) <= toleranceV) {
                return new Result(
                        voltages,
                        newVoltageV,
                        iteration,
                        true
                );
            }

            voltageGuessV = newVoltageV;
        }

        return new Result(
                voltages,
                voltageGuessV,
                maxIterations,
                false
        );
    }

    private CalculationNetwork withTrainLoad(
            CalculationNetwork baseNetwork,
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double voltageV
    ) {
        List<ElectricalElement> elements =
                new ArrayList<>(baseNetwork.elements());

        elements.add(new TrainLoadElement(
                feedingNodeId,
                returnNodeId,
                requestedPowerW,
                voltageV,
                systemParameters));

        return new CalculationNetwork(
                baseNetwork.nodes(),
                baseNetwork.branches(),
                baseNetwork.trainLoads(),
                elements
        );
    }

    private static double voltageBetween(
            Map<String, Real> voltages,
            String positiveNodeId,
            String negativeNodeId
    ) {
        return voltages.get(positiveNodeId).asDouble()
                - voltages.get(negativeNodeId).asDouble();
    }

    public record Result(
            Map<String, Real> voltages,
            double trainVoltageV,
            int iterations,
            boolean converged
    ) {
    }

    public record NetworkResult(
            Map<String, Real> voltages,
            int iterations,
            boolean converged
    ) {
    }

    public NetworkResult solve(
            CalculationNetwork baseNetwork,
            String referenceNodeId,
            Map<String, Double> requestedPowersW,
            int maxIterations,
            double toleranceV
    ) {
        Map<String, Double> voltageGuessByTrain =
                initialVoltageGuesses(baseNetwork);

        Map<String, Real> previousVoltages = Map.of();
        Map<String, Real> voltages = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {

            CalculationNetwork network =
                    withTrainLoads(
                            baseNetwork,
                            requestedPowersW,
                            voltageGuessByTrain
                    );

            AdmittanceSystem system =
                    new AdmittanceSystemBuilder().build(
                            network,
                            referenceNodeId,
                            List.of(),
                            previousVoltages
                    );

            try {
                voltages =
                        linearSystemSolver.solveVoltages(system);
            } catch (IllegalArgumentException e) {

                if (!e.getMessage().startsWith("Singular admittance matrix")) {
                    throw e;
                }

                return new NetworkResult(
                        previousVoltages,
                        iteration,
                        false
                );
            }

            double maxVoltageChangeV = 0.0;

            Map<String, Double> newVoltageGuessByTrain =
                    new java.util.LinkedHashMap<>();

            for (CalculationTrainLoad load : baseNetwork.trainLoads()) {

                double newVoltageV =
                        voltageBetween(
                                voltages,
                                load.feedingNodeId(),
                                load.returnNodeId()
                        );

                double oldVoltageV =
                        voltageGuessByTrain.get(load.trainId());

                maxVoltageChangeV =
                        Math.max(
                                maxVoltageChangeV,
                                Math.abs(newVoltageV - oldVoltageV)
                        );

                double relaxedVoltageV =
                        oldVoltageV
                                + RELAXATION
                                * (newVoltageV - oldVoltageV);

                newVoltageGuessByTrain.put(
                        load.trainId(),
                        relaxedVoltageV
                );
                System.out.printf(
                        "iteration=%d train=%s oldU=%.6f solvedU=%.6f relaxedU=%.6f%n",
                        iteration,
                        load.trainId(),
                        oldVoltageV,
                        newVoltageV,
                        relaxedVoltageV
                );            }

            System.out.printf(
                    "iteration=%d, maxVoltageChangeV=%.9f%n",
                    iteration,
                    maxVoltageChangeV
            );

            if (maxVoltageChangeV <= toleranceV) {
                return new NetworkResult(
                        voltages,
                        iteration,
                        true
                );
            }

            voltageGuessByTrain = newVoltageGuessByTrain;
            previousVoltages = voltages;
        }

        return new NetworkResult(
                voltages,
                maxIterations,
                false
        );
    }

    private Map<String, Double> initialVoltageGuesses(
            CalculationNetwork network
    ) {
        Map<String, Double> guesses =
                new java.util.LinkedHashMap<>();

        for (CalculationTrainLoad load : network.trainLoads()) {
            guesses.put(
                    load.trainId(),
                    systemParameters.uNominalV()
            );
        }

        return guesses;
    }

    private CalculationNetwork withTrainLoads(
            CalculationNetwork baseNetwork,
            Map<String, Double> requestedPowersW,
            Map<String, Double> voltageGuessByTrain
    ) {
        List<ElectricalElement> elements =
                new ArrayList<>();

        for (ElectricalElement element : baseNetwork.elements()) {
            if (!(element instanceof TrainLoadElement)) {
                elements.add(element);
            }
        }

        for (CalculationTrainLoad load : baseNetwork.trainLoads()) {

            double requestedPowerW =
                    requestedPowersW.getOrDefault(
                            load.trainId(),
                            load.pReqW().asDouble()
                    );

            double voltageV =
                    voltageGuessByTrain.get(load.trainId());

            elements.add(new TrainLoadElement(
                    load.feedingNodeId(),
                    load.returnNodeId(),
                    requestedPowerW,
                    voltageV,
                    systemParameters
            ));
        }

        return new CalculationNetwork(
                baseNetwork.nodes(),
                baseNetwork.branches(),
                baseNetwork.trainLoads(),
                elements
        );
    }
}