package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.TrainData;

public interface TrainBehavior {
    void stamp(RealVector V, RealMatrix G, RealVector J,
               TrainData tr, boolean motorEnabled, double vminDefault);
}
