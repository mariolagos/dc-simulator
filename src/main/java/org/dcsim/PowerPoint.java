package org.dcsim;

public record PowerPoint(
        double time,
        String position,
        double speed,
        double power,
        double voltage,
        double current
) {

    public PowerPoint shifted(double offsetSeconds) {
        return new PowerPoint(time + offsetSeconds, position, speed, power, voltage, current);
    }
}
