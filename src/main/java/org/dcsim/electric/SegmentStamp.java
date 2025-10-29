package org.dcsim.electric;

import java.util.*;

public final class SegmentStamp {
    private SegmentStamp() {}

    @FunctionalInterface
    public interface AdmittanceMatrix { void add(int r, int c, double v); }

    /** Gränsnod på ett basintervall (kan vara immutable eller dynamisk). */
    public static final class Boundary {
        public final int nodeId;   // nod-id (immutable eller tågankare)
        public final double sM;    // position [m] från basintervall-vänster
        public Boundary(int nodeId, double sM) { this.nodeId = nodeId; this.sM = sM; }
    }

    /** Stämpla seriekedja mellan konsekutiva gränser. */
    public static void stampByBoundaries(
            AdmittanceMatrix Y,
            double Rtotal, double lengthM,
            List<Boundary> boundaries,      // inkluderar både änd-immutables och mellanliggande dynamiska
            double Rmin                      // t.ex. 1e-6 Ω
    ) {
        if (lengthM <= 0) throw new IllegalArgumentException("lengthM must be > 0");
        final double L = lengthM;
        final List<Boundary> b = new ArrayList<>(boundaries);
        b.sort(Comparator.comparingDouble(bb -> bb.sM));

        for (int k = 0; k + 1 < b.size(); k++) {
            Boundary a = b.get(k), c = b.get(k+1);
            double dx = Math.max(0.0, Math.min(L, c.sM) - Math.max(0.0, a.sM));
            double Rk = Math.max(Rtotal * (dx / L), Rmin);      // bevarar total R (sum Rk ≈ Rtotal)
            stampResistor(Y, a.nodeId, c.nodeId, 1.0 / Rk);
        }
    }

    /** Klassisk resistorstämpling. */
    public static void stampResistor(AdmittanceMatrix Y, int p, int q, double g) {
        if (g == 0.0) return;
        Y.add(p, p,  g); Y.add(q, q,  g);
        Y.add(p, q, -g); Y.add(q, p, -g);
    }
}
