package org.dcsim.unit;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.impl.Behaviors;
import org.dcsim.solver.impl.DcStamps;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Stamp-only tester för diod-Substation via DcStamps.stampSubstation().
 * Verifierar:
 *  1) Spärr (bus över E, backfeed=false): ingen grenledningsförmåga mellan a-b,
 *     ingen källström; endast liten diagonal-läcka.
 *  2) Ledning (bus under E eller backfeed=true): Thevenin(E,R) -> Norton(g,E)
 *     dvs off-diagonaler ±g och J-injektion ±g*E.
 */
public class DiodeSubstationStampTests {

    private static final double EPS = 1e-6;
    private static final double G_LEAK_DIAG = 1e-9; // tiny numerical leak when blocked

    @Test
    public void diode_blocks_absorption_when_bus_above_E() {
        // Två noder: a=0, b=1 (b tänks som "återledare")
        final int a = 0, b = 1;
        final double E = 900.0;      // stationens EMF
        final double R = 0.01;       // internresistans
        final boolean allowBackfeed = false;

        // Sätt bus a över E -> dioden ska spärra
        RealVector V = new ArrayRealVector(new double[]{ E + 50.0, 0.0 });
        RealMatrix G = new Array2DRowRealMatrix(2, 2);
        RealVector J = new ArrayRealVector(2);

        SubstationData ss = new SubstationData("hej!", a, b, E, R, allowBackfeed);
        Behaviors.forSubstation(ss).stamp(V, G, J, ss, EPS, G_LEAK_DIAG);

        // När spärrad: inga off-diagonaler (ingen A-B-gren), ingen källström,
        // men liten diagonal-läcka läggs (G[a,a], G[b,b]) för numerik-stabilitet.
        assertEquals("G[a,b] should be 0 when blocked", 0.0, G.getEntry(a, b), 1e-15);
        assertEquals("G[b,a] should be 0 when blocked", 0.0, G.getEntry(b, a), 1e-15);

        assertEquals("J[a] should be 0 when blocked", 0.0, J.getEntry(a), 1e-15);
        assertEquals("J[b] should be 0 when blocked", 0.0, J.getEntry(b), 1e-15);

        assertEquals("G[a,a] tiny leak when blocked", G_LEAK_DIAG, G.getEntry(a, a), 1e-18);
        assertEquals("G[b,b] tiny leak when blocked", G_LEAK_DIAG, G.getEntry(b, b), 1e-18);
    }

    @Test
    public void diode_leads_forward_when_bus_below_E() {
        final int a = 0, b = 1;
        final double E = 900.0;
        final double R = 0.01;
        final boolean allowBackfeed = false;

        // Sätt bus a under E -> stationen i leverans (forward)
        RealVector V = new ArrayRealVector(new double[]{ E - 50.0, 0.0 });
        RealMatrix G = new Array2DRowRealMatrix(2, 2);
        RealVector J = new ArrayRealVector(2);

        DcStamps.stampSubstation(V, G, J, a, b, E, R, allowBackfeed, EPS, G_LEAK_DIAG);

        final double g = 1.0 / R;
        // Norton: resistor mellan a-b och källa I = g*E (a→b)
        assertEquals(+g, G.getEntry(a, a), 1e-12);
        assertEquals(+g, G.getEntry(b, b), 1e-12);
        assertEquals(-g, G.getEntry(a, b), 1e-12);
        assertEquals(-g, G.getEntry(b, a), 1e-12);

        assertEquals(+g * E, J.getEntry(a), 1e-9);
        assertEquals(-g * E, J.getEntry(b), 1e-9);
    }
}
