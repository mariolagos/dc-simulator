package org.dcsim.train;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * JUnit 4: epsilon edge tests around vmin and vmax.
 * Verifies strict boundary behavior for traction throttle and regen clamp.
 */
public class SimpleTrainControllerEdgeEpsilonTest {

    private static final double EPS = 1e-6;

    @Test
    public void traction_zero_at_vmin_and_positive_just_above() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        double vmin = 600.0, vmax = 900.0;
        double requested = 100_000.0;

        // Exactly at vmin => 0
        double pAt = ctrl.applyLimits(requested, vmin, vmin, vmax);
        assertEquals(0.0, pAt, 1e-9);

        // Just above vmin => > 0 but < requested
        double pAbove = ctrl.applyLimits(requested, vmin + EPS, vmin, vmax);
        assertTrue(pAbove > 0.0 && pAbove < requested);
    }

    @Test
    public void traction_full_just_above_band() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        double vmin = 600.0, vmax = 900.0;
        double hi = vmin * (1.0 + ctrl.getVminThrottleBandFrac());
        double requested = 80_000.0;

        // Slightly above band => unthrottled
        double p = ctrl.applyLimits(requested, hi + EPS, vmin, vmax);
        assertEquals(requested, p, 1e-9);
    }

    @Test
    public void regen_blocked_at_vmax_and_allowed_just_below() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        double vmin = 600.0, vmax = 900.0;
        double requested = -70_000.0;

        // At vmax => blocked
        double pAt = ctrl.applyLimits(requested, vmax, vmin, vmax);
        assertEquals(0.0, pAt, 1e-9);

        // Just below vmax => allowed
        double pBelow = ctrl.applyLimits(requested, vmax - EPS, vmin, vmax);
        assertEquals(requested, pBelow, 1e-9);
    }
}
