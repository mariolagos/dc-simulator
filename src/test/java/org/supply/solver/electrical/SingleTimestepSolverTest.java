package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.model.*;
import org.supply.solver.optimization.PowerAllocationOptimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

public class SingleTimestepSolverTest {

    @Test
    public void findsFeasibleRegenerationLevelForNonReceptiveNetwork() {

        SystemParameters systemParameters =
                systemParameters();

        SingleTimestepSolver solver =
                new SingleTimestepSolver(
                        systemParameters,
                        new LinearSystemSolver()
                );

        CalculationNetwork network =
                createSingleRegenTrainNetwork();

        double requestedPowerW =
                -1_400_000.0;

        double feasibleAlpha = 0.0;
        double infeasibleAlpha = 1.0;

        SingleTimestepSolver.NetworkResult feasibleResult =
                solver.solve(
                        network,
                        "R1",
                        Map.of("T1", 0.0),
                        200,
                        1e-3
                );

        assertTrue(feasibleResult.converged());

        for (int i = 0; i < 30; i++) {

            double alpha =
                    0.5 * (feasibleAlpha + infeasibleAlpha);

            double candidatePowerW =
                    alpha * requestedPowerW;

            SingleTimestepSolver.NetworkResult candidateResult =
                    solver.solve(
                            network,
                            "R1",
                            Map.of("T1", candidatePowerW),
                            200,
                            1e-3
                    );

            if (candidateResult.converged()) {
                feasibleAlpha = alpha;
                feasibleResult = candidateResult;
            } else {
                infeasibleAlpha = alpha;
            }
        }

        double feasiblePowerW =
                feasibleAlpha * requestedPowerW;

        System.out.printf(
                "requested=%.3f W  alpha=%.9f  feasible=%.3f W%n",
                requestedPowerW,
                feasibleAlpha,
                feasiblePowerW
        );

        assertTrue(feasibleAlpha >= 0.0);
        assertTrue(feasibleAlpha < 1.0);
    }

    @Test
    public void solvesTwoTrainLoadsInSameTimestep() {

        SystemParameters systemParameters =
                systemParameters();

        LinearSystemSolver linearSystemSolver =
                new LinearSystemSolver();

        SingleTimestepSolver solver =
                new SingleTimestepSolver(
                        systemParameters,
                        linearSystemSolver
                );

        CalculationNode f1 =
                new CalculationNode(
                        "F1",
                        "F1",
                        "section-1",
                        "track-1",
                        0.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode f2 =
                new CalculationNode(
                        "F2",
                        "F2",
                        "section-1",
                        "track-1",
                        1000.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode r1 =
                new CalculationNode(
                        "R1",
                        "R1",
                        "section-1",
                        "track-1",
                        0.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode r2 =
                new CalculationNode(
                        "R2",
                        "R2",
                        "section-1",
                        "track-1",
                        1000.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode t1f =
                new CalculationNode(
                        "train_T1_F",
                        "T1",
                        "section-1",
                        "track-1",
                        300.0,
                        CalculationNodeType.TRAIN_NODE
                );

        CalculationNode t1r =
                new CalculationNode(
                        "train_T1_R",
                        "T1",
                        "section-1",
                        "track-1",
                        300.0,
                        CalculationNodeType.TRAIN_NODE
                );

        CalculationNode t2f =
                new CalculationNode(
                        "train_T2_F",
                        "T2",
                        "section-1",
                        "track-1",
                        700.0,
                        CalculationNodeType.TRAIN_NODE
                );

        CalculationNode t2r =
                new CalculationNode(
                        "train_T2_R",
                        "T2",
                        "section-1",
                        "track-1",
                        700.0,
                        CalculationNodeType.TRAIN_NODE
                );

        List<CalculationNode> nodes =
                List.of(
                        f1,
                        r1,
                        t1f,
                        t1r,
                        t2f,
                        t2r
                );

        List<CalculationBranch> branches =
                List.of(
                        new CalculationBranch(
                                "F1-T1",
                                "line-F",
                                "F1",
                                "train_T1_F",
                                Real.fromDouble(0.03)
                        ),
                        new CalculationBranch(
                                "T1-T2",
                                "line-F",
                                "train_T1_F",
                                "train_T2_F",
                                Real.fromDouble(0.04)
                        ),
                        new CalculationBranch(
                                "R1-T1",
                                "line-R",
                                "R1",
                                "train_T1_R",
                                Real.fromDouble(0.03)
                        ),
                        new CalculationBranch(
                                "T1-T2-R",
                                "line-R",
                                "train_T1_R",
                                "train_T2_R",
                                Real.fromDouble(0.04)
                        )
                );

        List<CalculationTrainLoad> trainLoads =
                List.of(
                        new CalculationTrainLoad(
                                "T1",
                                "train_T1_F",
                                "train_T1_R",
                                Real.fromDouble(100_000.0)
                        ),
                        new CalculationTrainLoad(
                                "T2",
                                "train_T2_F",
                                "train_T2_R",
                                Real.fromDouble(100_000.0)
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
                        Real.fromDouble(0.1)
                )
        );

        CalculationNetwork network =
                new CalculationNetwork(
                        nodes,
                        branches,
                        trainLoads,
                        elements
                );

        Map<String, Double> requestedPowersW =
                Map.of(
                        "T1", 500_000.0,
                        "T2", 300_000.0
                );

        SingleTimestepSolver.NetworkResult result =
                solver.solve(
                        network,
                        "R1",
                        requestedPowersW,
                        200,
                        1e-3
                );

//        System.out.println(
//                "iterations=" + result.iterations()
//                        + ", converged=" + result.converged()
//        );

        assertThat(
                result.converged(),
                is(true)
        );

        assertThat(
                result.voltages().get("train_T1_F"),
                notNullValue()
        );

        assertThat(
                result.voltages().get("train_T1_R"),
                notNullValue()
        );

        assertThat(
                result.voltages().get("train_T2_F"),
                notNullValue()
        );

        assertThat(
                result.voltages().get("train_T2_R"),
                notNullValue()
        );

        double uT1 =
                result.voltages()
                        .get("train_T1_F")
                        .asDouble()
                        - result.voltages()
                        .get("train_T1_R")
                        .asDouble();

        double uT2 =
                result.voltages()
                        .get("train_T2_F")
                        .asDouble()
                        - result.voltages()
                        .get("train_T2_R")
                        .asDouble();

        assertThat(
                uT1,
                greaterThan(0.0)
        );

        assertThat(
                uT2,
                greaterThan(0.0)
        );
    }

    @Test
    public void solvesRegeneratingTrainWithDiodeSubstation() {

        SystemParameters systemParameters =
                systemParameters();

        SingleTimestepSolver solver =
                new SingleTimestepSolver(
                        systemParameters,
                        new LinearSystemSolver()
                );

        CalculationNode f1 =
                new CalculationNode(
                        "F1",
                        "F1",
                        "section-1",
                        "track-1",
                        0.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode r1 =
                new CalculationNode(
                        "R1",
                        "R1",
                        "section-1",
                        "track-1",
                        0.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode trainF =
                new CalculationNode(
                        "train_T1_F",
                        "T1",
                        "section-1",
                        "track-1",
                        500.0,
                        CalculationNodeType.TRAIN_NODE
                );

        CalculationNode trainR =
                new CalculationNode(
                        "train_T1_R",
                        "T1",
                        "section-1",
                        "track-1",
                        500.0,
                        CalculationNodeType.TRAIN_NODE
                );

        List<CalculationNode> nodes =
                List.of(
                        f1,
                        r1,
                        trainF,
                        trainR
                );

        List<CalculationBranch> branches =
                List.of(
                        new CalculationBranch(
                                "F1-T1",
                                "line-F",
                                "F1",
                                "train_T1_F",
                                Real.fromDouble(0.05)
                        ),
                        new CalculationBranch(
                                "R1-T1",
                                "line-R",
                                "R1",
                                "train_T1_R",
                                Real.fromDouble(0.05)
                        )
                );

        List<CalculationTrainLoad> trainLoads =
                List.of(
                        new CalculationTrainLoad(
                                "T1",
                                "train_T1_F",
                                "train_T1_R",
                                Real.fromDouble(-10_000.0)
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
                        Real.fromDouble(0.1)
                )
        );

        CalculationNetwork network =
                new CalculationNetwork(
                        nodes,
                        branches,
                        trainLoads,
                        elements
                );

        Map<String, Double> requestedPowersW =
                Map.of(
                        "T1", -10_000.0
                );

        SingleTimestepSolver.NetworkResult result =
                solver.solve(
                        network,
                        "R1",
                        requestedPowersW,
                        200,
                        1e-3
                );

        System.out.println(
                "regen test: iterations="
                        + result.iterations()
                        + ", converged="
                        + result.converged()
        );

        assertThat(
                result.voltages().get("train_T1_F"),
                notNullValue()
        );

        assertThat(
                result.voltages().get("train_T1_R"),
                notNullValue()
        );
    }

    private CalculationNetwork createSingleRegenTrainNetwork() {

        CalculationNode f1 =
                new CalculationNode(
                        "F1",
                        "F1",
                        "section-1",
                        "track-1",
                        0.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode r1 =
                new CalculationNode(
                        "R1",
                        "R1",
                        "section-1",
                        "track-1",
                        0.0,
                        CalculationNodeType.GRID_NODE
                );

        CalculationNode trainF =
                new CalculationNode(
                        "train_T1_F",
                        "T1",
                        "section-1",
                        "track-1",
                        500.0,
                        CalculationNodeType.TRAIN_NODE
                );

        CalculationNode trainR =
                new CalculationNode(
                        "train_T1_R",
                        "T1",
                        "section-1",
                        "track-1",
                        500.0,
                        CalculationNodeType.TRAIN_NODE
                );

        List<CalculationNode> nodes =
                List.of(
                        f1,
                        r1,
                        trainF,
                        trainR
                );

        List<CalculationBranch> branches =
                List.of(
                        new CalculationBranch(
                                "F1-T1",
                                "line-F",
                                "F1",
                                "train_T1_F",
                                Real.fromDouble(0.05)
                        ),
                        new CalculationBranch(
                                "R1-T1",
                                "line-R",
                                "R1",
                                "train_T1_R",
                                Real.fromDouble(0.05)
                        )
                );

        List<CalculationTrainLoad> trainLoads =
                List.of(
                        new CalculationTrainLoad(
                                "T1",
                                "train_T1_F",
                                "train_T1_R",
                                Real.fromDouble(-500_000.0)
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
                        Real.fromDouble(0.1)
                )
        );

        return new CalculationNetwork(
                nodes,
                branches,
                trainLoads,
                elements
        );
    }

    private static SystemParameters systemParameters() {
        return new SystemParameters(
                750.0,       // uNominalV
                500.0,       // uMinV
                600.0,       // uCutoffV
                900.0,       // uMaxV
                4_000.0,     // iMaxA
                3_000_000.0,
                3_000_000.0
        );
    }
}
