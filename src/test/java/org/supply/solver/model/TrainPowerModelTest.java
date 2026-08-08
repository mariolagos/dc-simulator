package org.supply.solver.model;

import org.junit.Test;

import static org.junit.Assert.*;

public final class TrainPowerModelTest {

    private static final double U_CUTOFF_V = 600.0;
    private static final double U_MIN_V = 700.0;
    private static final double I_TRAIN_MAX_A = 7000.0;

    @Test
    public void tractionCurrentIsZeroBelowLowerVoltageLimit() {
        assertEquals(
                0.0,
                tractionCurrentA(5_000_000.0, 550.0),
                1e-9
        );
    }

    @Test
    public void tractionCurrentIsZeroAtLowerVoltageLimit() {
        assertEquals(
                0.0,
                tractionCurrentA(5_000_000.0, U_CUTOFF_V),
                1e-9
        );
    }

    @Test
    public void tractionCurrentDeratesLinearlyBetweenLowerVoltageLimits() {
        double voltageV = 650.0;

        double expectedCurrentLimitA =
                0.5 * I_TRAIN_MAX_A;

        assertEquals(
                expectedCurrentLimitA,
                tractionCurrentA(5_000_000.0, voltageV),
                1e-9
        );
    }

    @Test
    public void tractionAllowsFullCurrentAtUpperLowVoltageLimit() {
        assertEquals(
                I_TRAIN_MAX_A,
                tractionCurrentA(10_000_000.0, U_MIN_V),
                1e-9
        );
    }

    @Test
    public void tractionCurrentIsBasedOnRequestedPowerAboveDeratingRegion() {
        assertEquals(
                3750.0,
                tractionCurrentA(3_000_000.0, 800.0),
                1e-9
        );
    }

    @Test
    public void tractionCurrentIsLimitedByTrainMaximumCurrent() {
        assertEquals(
                I_TRAIN_MAX_A,
                tractionCurrentA(10_000_000.0, 800.0),
                1e-9
        );
    }

    private static double tractionCurrentA(
            double requestedPowerW,
            double voltageV
    ) {
        double requestedCurrentA =
                requestedPowerW / voltageV;

        return Math.min(
                requestedCurrentA,
                tractionCurrentLimitA(voltageV)
        );
    }

    private static double tractionCurrentLimitA(double voltageV) {
        if (voltageV <= U_CUTOFF_V) {
            return 0.0;
        }

        if (voltageV < U_MIN_V) {
            return I_TRAIN_MAX_A
                    * (voltageV - U_CUTOFF_V)
                    / (U_MIN_V - U_CUTOFF_V);
        }

        return I_TRAIN_MAX_A;
    }
}