package org.supply.model;


import org.dcsim.math.Real;

class VoltageSample {
    public final double time;
    public final Real voltage;

    public VoltageSample(double time, Real voltage) {
        this.time = time;
        this.voltage = voltage;
    }
}
