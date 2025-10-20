package org.dcsim.solver.api;

import org.dcsim.math.Real;

public record SubstationData(
        String id,
        int a,                  // kompakt nodindex (från/buss)
        int b,                  // kompakt nodindex (till/jord/busbar ret)
        double emf_V,
        double rint_ohm,
        boolean allowBackfeed
) {
    public Real getRint_ohm() {
        return new Real(rint_ohm);
    }

    public Real getEmf_V() {
        return new Real(emf_V);
    }
}
