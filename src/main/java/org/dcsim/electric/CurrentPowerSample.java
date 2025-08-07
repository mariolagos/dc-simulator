package org.dcsim.electric;

import org.dcsim.math.Real;

public class CurrentPowerSample {
    public final double time;
    public final Real current;
    public final Real power;

    public CurrentPowerSample(double time, Real current, Real power) {
        this.time = time;
        this.current = current;
        this.power = power;
    }
}
