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
        Map<String, Real> voltages =
                solve(ElectricalTestCases.oneSubstationOneTrainLine());

        assertEquals(
                ElectricalTestCases.U_NOMINAL_V,
                trainVoltage(voltages),
                1e-6
        );    }

    @Test
    public void loadedCurrentInjectionProducesVoltageDrop() {
        CalculationNetwork network =
                ElectricalTestCases.oneSubstationOneTrainLine();

        double trainCurrentA = ElectricalTestCases.TEST_CURRENT_A;

        Map<String, Real> voltages =
                solve(
                        network,
                        List.of(
                                new CurrentInjection("F_TRAIN", Real.fromDouble(-trainCurrentA)),
                                new CurrentInjection("R_TRAIN", Real.fromDouble(trainCurrentA))
                        )
                );

        assertTrue(
                trainVoltage(voltages)
                        < ElectricalTestCases.U_NOMINAL_V
        );

        assertEquals(
                ElectricalTestCases.EXPECTED_TRACTION_V,
                trainVoltage(voltages),
                1e-6
        );
    }

    @Test
    public void loadedCurrentInjectionProducesExpectedBranchCurrents() {
        CalculationNetwork network =
                ElectricalTestCases.oneSubstationOneTrainLine();

        double trainCurrentA =
                ElectricalTestCases.TEST_CURRENT_A;

        Map<String, Real> voltages =
                solve(
                        network,
                        List.of(
                                new CurrentInjection("F_TRAIN", Real.fromDouble(-trainCurrentA)),
                                new CurrentInjection("R_TRAIN", Real.fromDouble(trainCurrentA))
                        )
                );

        assertEquals(ElectricalTestCases.TEST_CURRENT_A, branchCurrentA(voltages, "F_SUB", "F_TRAIN", 0.1), 1e-6);
        assertEquals(ElectricalTestCases.TEST_CURRENT_A, branchCurrentA(voltages, "R_TRAIN", "R_SUB", 0.1), 1e-6);
    }

    @Test
    public void trainLoadElementProducesExpectedVoltageDrop() {
        Map<String, Real> voltages =
                solve(
                        ElectricalTestCases
                                .oneSubstationOneTrainLineWithTrainPower(
                                        ElectricalTestCases.TEST_POWER_W
                                )
                );

        assertEquals(
                ElectricalTestCases.EXPECTED_TRACTION_V,
                trainVoltage(voltages),
                1e-6
        );
    }

    @Test
    public void regenerativeBrakingInjectsCurrentIntoNetwork() {
        Map<String, Real> voltages =
                solve(
                        ElectricalTestCases
                                .oneSubstationOneTrainLineWithTrainPower(
                                        -ElectricalTestCases.TEST_POWER_W
                                )
                );

        assertTrue(
                trainVoltage(voltages)
                        > ElectricalTestCases.U_NOMINAL_V
        );

        assertEquals(
                ElectricalTestCases.EXPECTED_REGEN_V,
                trainVoltage(voltages),
                1e-6
        );
    }

    private static Map<String, Real> solve(CalculationNetwork network) {
        return solve(network, List.of());
    }

    private static Map<String, Real> solve(
            CalculationNetwork network,
            List<CurrentInjection> currentInjections
    ) {
        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "R_SUB",
                        currentInjections
                );

        return new LinearSystemSolver().solveVoltages(system);
    }

    private static double trainVoltage(Map<String, Real> voltages) {
        return voltageBetween(voltages, "F_TRAIN", "R_TRAIN");
    }

    private static double voltageBetween(
            Map<String, Real> voltages,
            String positiveNodeId,
            String negativeNodeId
    ) {
        return voltages.get(positiveNodeId).asDouble()
                - voltages.get(negativeNodeId).asDouble();
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