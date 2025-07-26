package org.dcsim;

public record PowerPoint(
        double time,
        String position,
        double speed,
        double power,
        double voltage,
        double current
) {}
