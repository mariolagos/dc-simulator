package org.supply.app;

import org.junit.Test;
import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.electrical.LinearSystemSolver;
import org.supply.solver.electrical.SingleTimestepSolver;
import org.supply.solver.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DcSolverFallbackTest {

    private static final SystemParameters SYSTEM_PARAMETERS =
            new SystemParameters(
                    750.0,
                    500.0,
                    600.0,
                    900.0,
                    6000.0,
                    10_000_000.0,
                    3_600_000.0
            );

    @Test
    public void excessRegenerationDoesNotReduceMotoringTrain() {
        assertAllocation(
                -800_000.0,
                600_000.0,
                -800_000.0,
                600_000.0
        );
    }

    @Test
    public void balancedRegenerationAndMotoringAreFullyAccepted() {
        assertAllocation(
                -700_000.0,
                700_000.0,
                -700_000.0,
                700_000.0
        );
    }

    @Test
    public void regenerationCurtailmentDoesNotReduceMotoringPower() {
        CalculationNetwork network =
                twoTrainNetwork(
                        -600_000.0,
                        800_000.0
                );

        Map<String, Double> requestedPowersW =
                Map.of(
                        "TrainBrake", -600_000.0,
                        "TrainMotor", 800_000.0
                );

        DcSolver.SolveResult result =
                DcSolver.solveWithFallback(
                        SYSTEM_PARAMETERS,
                        network,
                        requestedPowersW,
                        Map.of()
                );

        assertEquals(
                800_000.0,
                result.allocatedPowersW().get("TrainMotor"),
                1_000.0
        );

        double allocatedBrakeW =
                result.allocatedPowersW().get("TrainBrake");

        assertTrue(allocatedBrakeW < 0.0);
        assertTrue(allocatedBrakeW > -600_000.0);
    }


    private void assertAllocation(
            double requestedBrakeW,
            double requestedMotorW,
            double expectedBrakeW,
            double expectedMotorW
    ) {
        CalculationNetwork network =
                twoTrainNetwork(
                        requestedBrakeW,
                        requestedMotorW
                );

        Map<String, Double> requestedPowersW =
                Map.of(
                        "TrainBrake", requestedBrakeW,
                        "TrainMotor", requestedMotorW
                );

        SingleTimestepSolver directSolver =
                new SingleTimestepSolver(
                        SYSTEM_PARAMETERS,
                        new LinearSystemSolver()
                );

        SingleTimestepSolver.NetworkResult direct =
                directSolver.solve(
                        network,
                        "R1",
                        requestedPowersW,
                        200,
                        1e-3,
                        Map.of()
                );

        assertTrue(
                "SingleTimestepSolver should converge without regeneration fallback",
                direct.converged()
        );

        DcSolver.SolveResult result =
                DcSolver.solveWithFallback(
                        SYSTEM_PARAMETERS,
                        network,
                        requestedPowersW,
                        Map.of()
                );

        assertEquals(
                expectedMotorW,
                result.allocatedPowersW().get("TrainMotor"),
                1_000.0
        );

        assertEquals(
                expectedBrakeW,
                result.allocatedPowersW().get("TrainBrake"),
                1_000.0
        );
    }

    private CalculationNetwork twoTrainNetwork(
            double brakePowerW,
            double motorPowerW
    ) {
        CalculationNode f1 =
                node("F1", 0.0);

        CalculationNode r1 =
                node("R1", 0.0);

        CalculationNode brakeF =
                node("train_Brake_F", 500.0);

        CalculationNode brakeR =
                node("train_Brake_R", 500.0);

        CalculationNode motorF =
                node("train_Motor_F", 1000.0);

        CalculationNode motorR =
                node("train_Motor_R", 1000.0);

        List<CalculationNode> nodes =
                List.of(
                        f1,
                        r1,
                        brakeF,
                        brakeR,
                        motorF,
                        motorR
                );

        List<CalculationBranch> branches =
                List.of(
                        new CalculationBranch(
                                "F1-Brake",
                                "line-F",
                                "F1",
                                "train_Brake_F",
                                Real.fromDouble(0.03)
                        ),
                        new CalculationBranch(
                                "Brake-Motor-F",
                                "line-F",
                                "train_Brake_F",
                                "train_Motor_F",
                                Real.fromDouble(0.04)
                        ),
                        new CalculationBranch(
                                "R1-Brake",
                                "line-R",
                                "R1",
                                "train_Brake_R",
                                Real.fromDouble(0.03)
                        ),
                        new CalculationBranch(
                                "Brake-Motor-R",
                                "line-R",
                                "train_Brake_R",
                                "train_Motor_R",
                                Real.fromDouble(0.04)
                        )
                );

        List<CalculationTrainLoad> trainLoads =
                List.of(
                        new CalculationTrainLoad(
                                "TrainBrake",
                                "train_Brake_F",
                                "train_Brake_R",
                                Real.fromDouble(brakePowerW)
                        ),
                        new CalculationTrainLoad(
                                "TrainMotor",
                                "train_Motor_F",
                                "train_Motor_R",
                                Real.fromDouble(motorPowerW)
                        )
                );

        List<ElectricalElement> elements =
                new ArrayList<>();

        elements.addAll(branches);

        elements.add(
                new DiodeSubstationElement(
                        "SS1",
                        "F1",
                        "R1",
                        Real.fromDouble(750.0),
                        Real.fromDouble(0.1),
                        true
                )
        );

        return new CalculationNetwork(
                nodes,
                branches,
                trainLoads,
                elements
        );
    }

    private static CalculationNode node(
            String id,
            double positionM
    ) {
        return new CalculationNode(
                id,
                null,
                "section-1",
                "SINGLE",
                positionM,
                CalculationNodeType.GRID_NODE
        );
    }

    @Test
    public void reportsRegenerativeCurrentFromVoltageDependentCharacteristic() {

        double terminalVoltageV = 872.738;
        double requestedPowerW = -900_000.0;

        double currentA =
                DcSolver.trainCurrentA(
                        SYSTEM_PARAMETERS,
                        requestedPowerW,
                        terminalVoltageV
                );

        assertEquals(
                -577.3,
                currentA,
                0.1
        );

        assertEquals(
                -503_800.0,
                terminalVoltageV * currentA,
                100.0
        );
    }
}