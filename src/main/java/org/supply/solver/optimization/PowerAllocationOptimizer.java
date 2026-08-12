package org.supply.solver.optimization;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;

import java.util.Arrays;
import java.util.Objects;

public final class PowerAllocationOptimizer {

    private static final int MAX_ITERATIONS = 1000;
    private static final int MAX_EVALUATIONS = 10_000;

    private static final int CHECK_FEASIBLE_COUNT = 10;
    private static final long RANDOM_SEED = 12345L;

    public Result optimize(
            double[] requestedPowersW,
            double[] lowerBoundsW,
            double[] upperBoundsW,
            CandidateEvaluator evaluator
    ) {
        Objects.requireNonNull(requestedPowersW, "requestedPowersW");
        Objects.requireNonNull(lowerBoundsW, "lowerBoundsW");
        Objects.requireNonNull(upperBoundsW, "upperBoundsW");
        Objects.requireNonNull(evaluator, "evaluator");

        validateDimensions(
                requestedPowersW,
                lowerBoundsW,
                upperBoundsW
        );

        int dimension = requestedPowersW.length;

        double[] initialGuess =
                initialGuess(
                        requestedPowersW,
                        lowerBoundsW,
                        upperBoundsW
                );

        double[] sigma =
                initialSigma(
                        lowerBoundsW,
                        upperBoundsW
                );

        int populationSize =
                Math.max(
                        4,
                        (int) Math.round(
                                4.0 + 3.0 * Math.log(dimension)
                        )
                );

        MultivariateFunction objective = point -> {
            CandidateEvaluation evaluation =
                    evaluator.evaluate(point);

            double mismatch =
                    powerMismatch(
                            point,
                            requestedPowersW
                    );

            return mismatch
                    + evaluation.penalty();
        };

        CMAESOptimizer optimizer =
                new CMAESOptimizer(
                        MAX_ITERATIONS,
                        0.0,
                        true,
                        0,
                        CHECK_FEASIBLE_COUNT,
                        new MersenneTwister(RANDOM_SEED),
                        false,
                        null
                );

        PointValuePair optimum =
                optimizer.optimize(
                        new MaxEval(MAX_EVALUATIONS),
                        new ObjectiveFunction(objective),
                        GoalType.MINIMIZE,
                        new InitialGuess(initialGuess),
                        new SimpleBounds(
                                lowerBoundsW,
                                upperBoundsW
                        ),
                        new CMAESOptimizer.PopulationSize(
                                populationSize
                        ),
                        new CMAESOptimizer.Sigma(
                                sigma
                        )
                );

        double[] allocatedPowersW =
                optimum.getPoint();

        CandidateEvaluation finalEvaluation =
                evaluator.evaluate(allocatedPowersW);

        return new Result(
                allocatedPowersW,
                optimum.getValue(),
                finalEvaluation
        );
    }

    private static double powerMismatch(
            double[] point,
            double[] requestedPowersW
    ) {
        double sum = 0.0;

        for (int i = 0; i < point.length; i++) {
            double error =
                    point[i] - requestedPowersW[i];

            sum += error * error;
        }

        return sum;
    }

    private static double[] initialGuess(
            double[] requestedPowersW,
            double[] lowerBoundsW,
            double[] upperBoundsW
    ) {
        double[] guess =
                new double[requestedPowersW.length];

        for (int i = 0; i < guess.length; i++) {
            guess[i] =
                    clamp(
                            requestedPowersW[i],
                            lowerBoundsW[i],
                            upperBoundsW[i]
                    );
        }

        return guess;
    }

    private static double[] initialSigma(
            double[] lowerBoundsW,
            double[] upperBoundsW
    ) {
        double[] sigma =
                new double[lowerBoundsW.length];

        for (int i = 0; i < sigma.length; i++) {
            double range =
                    upperBoundsW[i]
                            - lowerBoundsW[i];

            if (range <= 0.0) {
                throw new IllegalArgumentException(
                        "Power bound range must be positive for index "
                                + i
                );
            }

            sigma[i] =
                    Math.max(
                            1.0,
                            0.20 * range
                    );
        }

        return sigma;
    }

    private static double clamp(
            double value,
            double lower,
            double upper
    ) {
        return Math.max(
                lower,
                Math.min(value, upper)
        );
    }

    private static void validateDimensions(
            double[] requestedPowersW,
            double[] lowerBoundsW,
            double[] upperBoundsW
    ) {
        int n = requestedPowersW.length;

        if (n == 0) {
            throw new IllegalArgumentException(
                    "At least one train is required"
            );
        }

        if (lowerBoundsW.length != n
                || upperBoundsW.length != n) {
            throw new IllegalArgumentException(
                    "Power arrays must have equal length"
            );
        }

        for (int i = 0; i < n; i++) {
            if (lowerBoundsW[i] >= upperBoundsW[i]) {
                throw new IllegalArgumentException(
                        "Invalid power bounds at index "
                                + i
                                + ": "
                                + lowerBoundsW[i]
                                + " .. "
                                + upperBoundsW[i]
                );
            }
        }
    }

    @FunctionalInterface
    public interface CandidateEvaluator {

        CandidateEvaluation evaluate(
                double[] powersW
        );
    }

    public record CandidateEvaluation(
            double penalty
    ) {

        public CandidateEvaluation {
            if (!Double.isFinite(penalty)
                    || penalty < 0.0) {
                throw new IllegalArgumentException(
                        "Penalty must be finite and non-negative"
                );
            }
        }

        public static CandidateEvaluation feasible() {
            return new CandidateEvaluation(0.0);
        }

        public static CandidateEvaluation penalty(
                double penalty
        ) {
            return new CandidateEvaluation(penalty);
        }
    }

    public record Result(
            double[] allocatedPowersW,
            double objectiveValue,
            CandidateEvaluation evaluation
    ) {

        public Result {
            allocatedPowersW =
                    Arrays.copyOf(
                            allocatedPowersW,
                            allocatedPowersW.length
                    );
        }

        @Override
        public double[] allocatedPowersW() {
            return Arrays.copyOf(
                    allocatedPowersW,
                    allocatedPowersW.length
            );
        }
    }
}