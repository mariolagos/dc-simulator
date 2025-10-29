package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.SubstationData;

/** Diode-like feeder: forward if dV <= E+eps OR backfeed is allowed. */
public enum DiodeSubstationBehavior implements SubstationBehavior {
    INSTANCE;

    @Override
    public void stamp(RealVector V, RealMatrix G, RealVector J,
                      SubstationData ss, double eps, double gLeakDiag) {
        DcStamps.stampSubstation(
                V, G, J,
                ss.a(), ss.b(),
                ss.emf_V(), ss.rint_ohm(),
                ss.allowBackfeed(),
                eps, gLeakDiag
        );
    }

    @Override
    public boolean isActive(RealVector V, SubstationData ss, double eps) {
        double dV = V.getEntry(ss.a()) - V.getEntry(ss.b());
        return ss.allowBackfeed() || (dV <= ss.emf_V() + eps);
    }
}
