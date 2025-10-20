package org.dcsim.solver.build;

import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.SubstationData;

public final class Seed {
    private Seed(){}

    /**
     * Enkel seed: alla noder = 0, för varje station sätt buss (a) till E - 1 mV.
     * Jordindex ges separat i DcNet.
     */
    public static double[] voltageGuess(DcNet net) {
        double[] V = new double[net.n];
        V[net.groundIndex] = 0.0;
        for (SubstationData S : net.substations) {
            V[S.a()] = Math.max(V[S.a()], S.emf_V() - 1e-3);
        }
        return V;
    }
}
