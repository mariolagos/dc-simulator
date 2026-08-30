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
            String trainId,
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
                    trainId,
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

            if (requestedPowerW < 0.0) {
                elements.add(new RegenerativeTrainElement(
                        load.trainId(),
                        load.feedingNodeId(),
                        load.returnNodeId(),
                        requestedPowerW,
                        voltageV,
                        850.0,
                        systemParameters.uMaxV()
                ));
            } else {
                elements.add(new TrainLoadElement(
                        load.trainId(),
                        load.feedingNodeId(),
                        load.returnNodeId(),
                        requestedPowerW,
                        voltageV,
                        systemParameters
                ));
            }
        }

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
        return solve(
                baseNetwork,
                referenceNodeId,
                requestedPowersW,
                maxIterations,
                toleranceV,
                Map.of()
        );
    }

    public NetworkResult solve(
            CalculationNetwork baseNetwork,
            String referenceNodeId,
            Map<String, Double> requestedPowersW,
            int maxIterations,
            double toleranceV,
            Map<String, Real> initialVoltages
    ) {
        Map<String, Double> voltageGuessByTrain =
                initialVoltageGuesses(
                        baseNetwork,
                        requestedPowersW
                );
        Map<String, Real> previousVoltages =
                initialVoltages == null
                        ? Map.of()
                        : initialVoltages;

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

//                if (!e.getMessage().startsWith("Singular admittance matrix")) {
//                    throw e;
//                }

                System.out.println(
                        "SINGULAR at iteration=" + iteration
                                + ", voltageGuessByTrain=" + voltageGuessByTrain
                );

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
            }

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
            CalculationNetwork network,
            Map<String, Double> requestedPowersW
    ) {
        Map<String, Double> guesses =
                new java.util.LinkedHashMap<>();

        for (CalculationTrainLoad load : network.trainLoads()) {

            double requestedPowerW =
                    requestedPowersW.getOrDefault(
                            load.trainId(),
                            load.pReqW().asDouble()
                    );

            double initialVoltageV =
                    requestedPowerW < 0.0
                            ? 850.0
                            : systemParameters.uNominalV();

            guesses.put(
                    load.trainId(),
                    initialVoltageV
            );
        }

        return guesses;
    }

    private CalculationNetwork withTrainLoad(
            String trainId,
            CalculationNetwork baseNetwork,
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double voltageV
    ) {
        List<ElectricalElement> elements =
                new ArrayList<>(baseNetwork.elements());

        elements.add(new TrainLoadElement(
                trainId,
                feedingNodeId,
                returnNodeId,
                requestedPowerW,
                voltageV,
                systemParameters
        ));

        return new CalculationNetwork(
                baseNetwork.nodes(),
                baseNetwork.branches(),
                baseNetwork.trainLoads(),
                elements
        );
    }
}