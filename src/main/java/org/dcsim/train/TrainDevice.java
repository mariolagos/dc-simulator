package org.dcsim.train;

import org.dcsim.grid.PowerPort;

/**
 * Train ↔ Grid adapter that applies controller limits and pushes the resulting power to the grid.
 *
 * Sign convention:
 *   +P = traction (consumes power from DC bus)
 *   -P = regenerative braking (injects power into DC bus)
 *
 * Typical loop per time step:
 *   1) Read bus voltage at the train's node (from PowerPort)
 *   2) Apply controller limits given vmin/vmax
 *   3) Push the applied power to the grid via PowerPort
 */
public final class TrainDevice {

    private final PowerPort port;
    private final TrainController controller;
    private final double vmin;
    private final double vmax;

    // For inspection/telemetry
    private double lastRequestedPowerW;
    private double lastAppliedPowerW;
    private double lastMeasuredVoltageV;

    public TrainDevice(PowerPort port, TrainController controller, double vmin, double vmax) {
        if (port == null) throw new IllegalArgumentException("port must not be null");
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.port = port;
        this.controller = controller;
        this.vmin = vmin;
        this.vmax = vmax;
    }

    /** Step using the port's measured voltage. Preferred in production/integration. */
    public void step(double requestedPowerW) {
        double v = safeVoltage(port.getVoltage());
        step(requestedPowerW, v);
    }

    /**
     * Step with an explicit measured voltage (useful in unit tests).
     * Applies controller limits and forwards the resulting power to the grid.
     */
    public void step(double requestedPowerW, double measuredVoltageV) {
        this.lastRequestedPowerW = requestedPowerW;
        this.lastMeasuredVoltageV = measuredVoltageV;

        double applied = controller.applyLimits(requestedPowerW, measuredVoltageV, vmin, vmax);
        this.lastAppliedPowerW = applied;

        // Push to grid (+P = consume, -P = inject)
        port.setRequestedPower(applied);
    }

    /** Immediately idle (zero power). */
    public void idle() {
        this.lastRequestedPowerW = 0.0;
        this.lastAppliedPowerW = 0.0;
        port.setRequestedPower(0.0);
    }

    // ──────────────────────────── getters for assertions/telemetry ────────────────────────────
    public double getLastRequestedPowerW() { return lastRequestedPowerW; }
    public double getLastAppliedPowerW()   { return lastAppliedPowerW; }
    public double getLastMeasuredVoltageV(){ return lastMeasuredVoltageV; }

    public double getVmin() { return vmin; }
    public double getVmax() { return vmax; }

    public PowerPort getPort() { return port; }

    private static double safeVoltage(double v) {
        // Avoid division-by-zero issues downstream if someone maps P→I (I = P/V)
        return (v > 1e-9) ? v : 1e-9;
    }
}
