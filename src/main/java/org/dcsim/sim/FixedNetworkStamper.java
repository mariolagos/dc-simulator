package org.dcsim.sim;

import org.dcsim.electric.AnchorStamp;

/** Adapters you must implement to hook into your model/solver. */
@FunctionalInterface
public interface FixedNetworkStamper {
    void stamp(AnchorStamp.AdmittanceMatrix Y, AnchorStamp.CurrentVector J);
}
