package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.testsupport.ElectricalTestCases;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public final class ElectricalSolverValidationTest {

    @Test
    public void openCircuitProducesNominalTrainVoltage() {
        CalculationNetwork network =
                ElectricalTestCases.oneSubstationOneTrainLine();

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "R_SUB");

        Map<String, Real> voltages =
                new LinearSystemSolver().solveVoltages(system);

        double trainVoltage =
                voltageBetween(voltages, "F_TRAIN", "R_TRAIN");

        assertEquals(780.0, trainVoltage, 1e-6);
    }

    @Test
    public void loadedCurrentInjectionProducesVoltageDrop() {
        CalculationNetwork network =
                ElectricalTestCases.oneSubstationOneTrainLine();

        double trainCurrentA = 1000.0;

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "R_SUB",
                        List.of(
                                new CurrentInjection(
                                        "F_TRAIN",
                                        Real.fromDouble(-trainCurrentA)
                                ),
                                new CurrentInjection(
                                        "R_TRAIN",
                                        Real.fromDouble(trainCurrentA)
                                )
                        )
                );

        Map<String, Real> voltages =
                new LinearSystemSolver().solveVoltages(system);

        double trainVoltage =
                voltageBetween(voltages, "F_TRAIN", "R_TRAIN");

        assertTrue(
                "Expected loaded train voltage below open-circuit voltage",
                trainVoltage < 780.0
        );

        assertEquals(480.0, trainVoltage, 1e-6);
    }

    private static double voltageBetween(
            Map<String, Real> voltages,
            String positiveNodeId,
            String negativeNodeId
    ) {
        return voltages.get(positiveNodeId).asDouble()
                - voltages.get(negativeNodeId).asDouble();
    }

    @Test
    public void loadedCurrentInjectionProducesExpectedBranchCurrents() {
        CalculationNetwork network =
                ElectricalTestCases.oneSubstationOneTrainLine();

        double trainCurrentA = 1000.0;

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "R_SUB",
                        List.of(
                                new CurrentInjection(
                                        "F_TRAIN",
                                        Real.fromDouble(-trainCurrentA)
                                ),
                                new CurrentInjection(
                                        "R_TRAIN",
                                        Real.fromDouble(trainCurrentA)
                                )
                        )
                );

        Map<String, Real> voltages =
                new LinearSystemSolver().solveVoltages(system);

        assertEquals(
                1000.0,
                branchCurrentA(voltages, "F_SUB", "F_TRAIN", 0.1),
                1e-6
        );

        assertEquals(
                1000.0,
                branchCurrentA(voltages, "R_TRAIN", "R_SUB", 0.1),
                1e-6
        );
    }

    private static double branchCurrentA(
            Map<String, Real> voltages,
            String fromNodeId,
            String toNodeId,
            double resistanceOhm
    ) {
        return (voltages.get(fromNodeId).asDouble()
                - voltages.get(toNodeId).asDouble())
                / resistanceOhm;
    }
}