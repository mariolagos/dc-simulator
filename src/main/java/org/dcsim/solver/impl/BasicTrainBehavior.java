package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.TrainData;

/** Basic train model (motor/regen with vmin/vmax + imax). */
public enum BasicTrainBehavior implements TrainBehavior {
    INSTANCE;

    @Override
    public void stamp(RealVector V, RealMatrix G, RealVector J,
                      TrainData tr, boolean motorEnabled, double vminDefault) {
        DcStamps.stampTrain(V, G, J, tr, vminDefault, motorEnabled);
    }
}
