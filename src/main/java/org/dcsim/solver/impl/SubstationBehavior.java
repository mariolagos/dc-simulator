package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.SubstationData;

public interface SubstationBehavior {
    void stamp(RealVector V, RealMatrix G, RealVector J,
               SubstationData ss, double eps, double gLeakDiag);
    boolean isActive(RealVector V, SubstationData ss, double eps);
}
