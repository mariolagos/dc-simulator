package org.dcsim.electric;


import org.dcsim.math.Real;

// Legacy electric model.
// Kept temporarily while node_id-based model loading migrates to org.supply.model.GridModel.
@Deprecated
class VoltageSample {
    public final double time;
    public final Real voltage;

    public VoltageSample(double time, Real voltage) {
        this.time = time;
        this.voltage = voltage;
    }
}
