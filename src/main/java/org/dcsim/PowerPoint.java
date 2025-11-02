package org.dcsim;

/**
 * Immutable sample point for power/kinematics.
 * power is in W, positionM in meters, speedMS in m/s.
 */
public final class PowerPoint {
    private final double time;     // seconds
    private String position; // legacy text position
    private double speed;    // legacy speed (unit depends on legacy source)
    private final double power;    // W
    private final double voltage;
    private final double current;

    // === NEW numeric kinematics (optional) ===
    private final Double positionM; // meters (nullable)
    private Double speedMS;   // m/s (nullable)

    /**
     * Legacy constructor (kept intact).
     */
    public PowerPoint(double time, String position, double speed, double power, double voltage, double current) {
        this.time = time;
        this.position = position;
        this.speed = speed;
        this.power = power;
        this.voltage = voltage;
        this.current = current;
        this.positionM = null;
        this.speedMS = null;
    }

    /**
     * New constructor with numeric kinematics.
     */
    public PowerPoint(double time, double positionM, double speedMS, double power, double voltage, double current) {
        this.time = time;
        this.position = String.valueOf(positionM);
        this.speed = speedMS;
        this.power = power;
        this.voltage = voltage;
        this.current = current;
        this.positionM = positionM;
        this.speedMS = speedMS;
    }

    // --- getters (keep existing API names) ---
    public double time() {
        return time;
    }

    public double power() {
        return power;
    }

    public String position() {
        return position;
    }

    // new accessors
    public boolean hasPositionM() {
        return positionM != null;
    }

    public double positionM() {
        return (positionM != null) ? positionM : Double.NaN;
    }

    public void setPositionM(double posM) {
    }

    public boolean hasSpeedMS() {
        return speedMS != null;
    }

    public double speedMS() {
        return (speedMS != null) ? speedMS : speed;
    } // legacy speed as fallback

    public void setSpeedMps(double v) {
        speedMS = v;
    }
}
