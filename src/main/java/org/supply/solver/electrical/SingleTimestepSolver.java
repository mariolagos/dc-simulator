package org.supply.solver.electrical;

import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.model.*;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SingleTimestepSolver {

    private  static SystemParameters systemParameters;
    private static LinearSystemSolver linearSystemSolver;

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

    private static CalculationNetwork withTrainLoad(
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
}