package org.dcsim.train;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * JUnit 4 tests for SimpleTrainController edge cases around vmin/vmax.
 */
public class SimpleTrainControllerTest {

    @Test
    public void traction_zero_at_vmin() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10); // 10% band
        double vmin = 600.0, vmax = 900.0;
        double p = ctrl.applyLimits(100_000.0, /*V=*/600.0, vmin, vmax);
        assertEquals("Traction must be fully throttled at vmin", 0.0, p, 1e-9);
    }

    @Test
    public void traction_partial_within_band() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        double vmin = 600.0, vmax = 900.0;
        double v = 630.0; // midway in [600, 660] => ~50% scale
        double requested = 100_000.0;
        double p = ctrl.applyLimits(requested, v, vmin, vmax);
        assertTrue("Traction should be throttled but > 0 in the band", p > 0.0 && p < requested);
        // Expected approx 50%:
        assertEquals(50_000.0, p, 5_000.0);
    }

    @Test
    public void traction_full_above_band() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        double vmin = 600.0, vmax = 900.0;
        double v = 700.0; // above 660
        double requested = 80_000.0;
        double p = ctrl.applyLimits(requested, v, vmin, vmax);
        assertEquals("Above band, traction should be unthrottled", requested, p, 1e-9);
    }

    @Test
    public void regen_blocked_at_vmax() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        double vmin = 600.0, vmax = 900.0;
        double p = ctrl.applyLimits(-70_000.0, /*V=*/900.0, vmin, vmax);
        assertEquals("Regen must be blocked at/above vmax", 0.0, p, 1e-9);
    }

    @Test
    public void regen_allowed_below_vmax() {
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        double vmin = 600.0, vmax = 900.0;
        double p = ctrl.applyLimits(-70_000.0, /*V=*/895.0, vmin, vmax);
        assertEquals("Regen should pass through below vmax", -70_000.0, p, 1e-9);
    }
}
