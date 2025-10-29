package org.dcsim.train;

/**
 * Train power controller interface.
 *
 * Sign convention:
 *   +P = traction (consumes power from the DC bus)
 *   -P = regenerative braking (injects power into the DC bus)
 *
 * Implementations should apply operational limits based on the measured DC bus
 * voltage and controller envelope (vmin/vmax).
 */
public interface TrainController {

    /**
     * Apply controller limits.
     *
     * @param requestedPowerW  desired power (+ traction, - regen)
     * @param measuredVoltageV DC bus voltage at the train's node
     * @param vminV            minimum allowed operating voltage
     * @param vmaxV            maximum allowed operating voltage
     * @return the power to apply after limits (same sign convention)
     */
    double applyLimits(double requestedPowerW, double measuredVoltageV, double vminV, double vmaxV);
}
