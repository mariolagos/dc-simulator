package org.supply.solver.optimization;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import org.supply.solver.optimization.PowerAllocationOptimizer;

public class PowerAllocationOptimizerTest {

    @Test
    public void findsRequestedPowersWhenAllRequestsAreFeasible() {

        double[] requested = {
                2_000_000.0,
                1_000_000.0,
                -1_500_000.0
        };

        double[] lowerBounds = {
                0.0,
                0.0,
                -1_500_000.0
        };

        double[] upperBounds = {
                2_000_000.0,
                1_000_000.0,
                0.0
        };

        PowerAllocationOptimizer optimizer =
                new PowerAllocationOptimizer();

        PowerAllocationOptimizer.Result result =
                optimizer.optimize(
                        requested,
                        lowerBounds,
                        upperBounds,
                        powersW ->
                                PowerAllocationOptimizer
                                        .CandidateEvaluation
                                        .feasible()
                );

        double[] allocated =
                result.allocatedPowersW();

        assertThat(
                allocated[0],
                closeTo(2_000_000.0, 1_000.0)
        );

        assertThat(
                allocated[1],
                closeTo(1_000_000.0, 1_000.0)
        );

        assertThat(
                allocated[2],
                closeTo(-1_500_000.0, 1_000.0)
        );
    }

    @Test
    public void reducesRequestedPowersWhenNetworkCapacityIsLimited() {

        double[] requested = {
                2_000_000.0,
                1_000_000.0,
                -500_000.0
        };

        double[] lowerBounds = {
                0.0,
                0.0,
                -500_000.0
        };

        double[] upperBounds = {
                2_000_000.0,
                1_000_000.0,
                0.0
        };

        double networkCapacityW = 1_500_000.0;
        double penaltyWeight = 1_000.0;

        PowerAllocationOptimizer optimizer =
                new PowerAllocationOptimizer();

        PowerAllocationOptimizer.Result result =
                optimizer.optimize(
                        requested,
                        lowerBounds,
                        upperBounds,
                        powersW -> {
                            double netPower =
                                    powersW[0]
                                            + powersW[1]
                                            + powersW[2];

                            double violationW =
                                    Math.max(
                                            0.0,
                                            netPower - networkCapacityW
                                    );

                            double penalty =
                                    penaltyWeight
                                            * violationW
                                            * violationW;

                            return PowerAllocationOptimizer
                                    .CandidateEvaluation
                                    .penalty(penalty);
                        }
                );

        double[] allocated =
                result.allocatedPowersW();

        double netPower =
                allocated[0]
                        + allocated[1]
                        + allocated[2];

        assertThat(
                netPower,
                closeTo(
                        networkCapacityW,
                        5_000.0
                )
        );

        assertThat(
                allocated[0],
                lessThanOrEqualTo(requested[0])
        );

        assertThat(
                allocated[1],
                lessThanOrEqualTo(requested[1])
        );

        assertThat(
                allocated[2],
                greaterThanOrEqualTo(requested[2])
        );
    }

    @Test
    public void limitsRegenerationWhenNetworkIsPartiallyReceptive() {

        double[] requested = {
                1_000_000.0,
                500_000.0,
                -2_000_000.0
        };

        double[] lowerBounds = {
                0.0,
                0.0,
                -2_000_000.0
        };

        double[] upperBounds = {
                1_000_000.0,
                500_000.0,
                0.0
        };

        double maxAcceptedRegenW = -800_000.0;
        double penaltyWeight = 1_000.0;

        PowerAllocationOptimizer optimizer =
                new PowerAllocationOptimizer();

        PowerAllocationOptimizer.Result result =
                optimizer.optimize(
                        requested,
                        lowerBounds,
                        upperBounds,
                        powersW -> {
                            double regenViolationW =
                                    Math.max(
                                            0.0,
                                            maxAcceptedRegenW - powersW[2]
                                    );

                            double penalty =
                                    penaltyWeight
                                            * regenViolationW
                                            * regenViolationW;

                            return PowerAllocationOptimizer
                                    .CandidateEvaluation
                                    .penalty(penalty);
                        }
                );

        double[] allocated =
                result.allocatedPowersW();

        assertThat(
                allocated[0],
                closeTo(1_000_000.0, 1_000.0)
        );

        assertThat(
                allocated[1],
                closeTo(500_000.0, 1_000.0)
        );

        assertThat(
                allocated[2],
                closeTo(-800_000.0, 5_000.0)
        );
    }

    @Test
    public void blocksRegenerationWhenNetworkIsNotReceptive() {

        double[] requested = {
                1_000_000.0,
                500_000.0,
                -2_000_000.0
        };

        double[] lowerBounds = {
                0.0,
                0.0,
                -2_000_000.0
        };

        double[] upperBounds = {
                1_000_000.0,
                500_000.0,
                0.0
        };

        double penaltyWeight = 1_000.0;

        PowerAllocationOptimizer optimizer =
                new PowerAllocationOptimizer();

        PowerAllocationOptimizer.Result result =
                optimizer.optimize(
                        requested,
                        lowerBounds,
                        upperBounds,
                        powersW -> {
                            double regenViolationW =
                                    Math.max(
                                            0.0,
                                            -powersW[2]
                                    );

                            double penalty =
                                    penaltyWeight
                                            * regenViolationW
                                            * regenViolationW;

                            return PowerAllocationOptimizer
                                    .CandidateEvaluation
                                    .penalty(penalty);
                        }
                );

        double[] allocated =
                result.allocatedPowersW();

        assertThat(
                allocated[0],
                closeTo(1_000_000.0, 1_000.0)
        );

        assertThat(
                allocated[1],
                closeTo(500_000.0, 1_000.0)
        );

        assertThat(
                allocated[2],
                closeTo(0.0, 5_000.0)
        );
    }

    @Test
    public void allocatesLimitedReceptivityBetweenMultipleRegeneratingTrains() {

        double[] requested = {
                500_000.0,     // T1 motoring
                -1_200_000.0,  // T2 regen
                -800_000.0     // T3 regen
        };

        double[] lowerBounds = {
                0.0,
                -1_200_000.0,
                -800_000.0
        };

        double[] upperBounds = {
                500_000.0,
                0.0,
                0.0
        };

        // Network can accept at most 1 MW total regeneration.
        double maxAcceptedRegenW = 1_000_000.0;
        double penaltyWeight = 1_000.0;

        PowerAllocationOptimizer optimizer =
                new PowerAllocationOptimizer();

        PowerAllocationOptimizer.Result result =
                optimizer.optimize(
                        requested,
                        lowerBounds,
                        upperBounds,
                        powersW -> {
                            double totalRegenW =
                                    Math.max(0.0, -powersW[1])
                                            + Math.max(0.0, -powersW[2]);

                            double violationW =
                                    Math.max(
                                            0.0,
                                            totalRegenW - maxAcceptedRegenW
                                    );

                            double penalty =
                                    penaltyWeight
                                            * violationW
                                            * violationW;

                            return PowerAllocationOptimizer
                                    .CandidateEvaluation
                                    .penalty(penalty);
                        }
                );

        double[] allocated =
                result.allocatedPowersW();

        double totalAcceptedRegenW =
                Math.max(0.0, -allocated[1])
                        + Math.max(0.0, -allocated[2]);

        assertThat(
                allocated[0],
                closeTo(500_000.0, 1_000.0)
        );

        assertThat(
                totalAcceptedRegenW,
                closeTo(1_000_000.0, 5_000.0)
        );

        assertThat(
                allocated[1],
                closeTo(-700_000.0, 5_000.0)
        );

        assertThat(
                allocated[2],
                closeTo(-300_000.0, 5_000.0)
        );
    }
}