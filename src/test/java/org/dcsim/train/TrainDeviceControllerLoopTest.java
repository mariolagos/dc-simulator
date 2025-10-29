package org.dcsim.train;

import org.junit.Test;
import static org.junit.Assert.*;
import org.dcsim.grid.PowerPort;

/**
 * JUnit 4 tests for TrainDevice + controller-in-loop using a tiny port stub.
 * Uses a local TestPort to avoid external dependencies.
 */
public class TrainDeviceControllerLoopTest {

    // Minimal inline port stub matching org.dcsim.grid.PowerPort
    static final class TestPort implements PowerPort {
        private double v;
        private double p;

        TestPort(double initialV) { this.v = initialV; }

        @Override public double getVoltage() { return v; }
        public void setVoltage(double v) { this.v = v; }

        @Override public void setRequestedPower(double watts) { this.p = watts; }
        @Override public double getRequestedPower() { return p; }
        @Override public int getNodeId() { return -1; }
    }

    @Test
    public void step_throttles_traction_near_vmin_and_pushes_to_port() {
        double vmin = 600.0, vmax = 900.0;
        TestPort port = new TestPort(610.0); // inside throttle band
        TrainController ctrl = new SimpleTrainController(0.10);
        TrainDevice dev = new TrainDevice(port, ctrl, vmin, vmax);

        dev.step(100_000.0); // requested traction
        double applied = port.getRequestedPower();

        assertTrue("Applied power should be throttled but positive", applied > 0.0 && applied < 100_000.0);
        assertEquals(applied, dev.getLastAppliedPowerW(), 1e-9);
    }

    @Test
    public void step_clamps_regen_at_or_above_vmax() {
        double vmin = 600.0, vmax = 900.0;
        TestPort port = new TestPort(905.0); // at/above vmax
        TrainController ctrl = new SimpleTrainController(0.10);
        TrainDevice dev = new TrainDevice(port, ctrl, vmin, vmax);

        dev.step(-80_000.0); // requested regen
        assertEquals("Regen must be clamped to 0 at/above vmax", 0.0, port.getRequestedPower(), 1e-9);
    }
}
