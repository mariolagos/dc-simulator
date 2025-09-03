// AnchorStampTest.java (JUnit 5)
import org.dcsim.electric.AnchorStamp;
import org.junit.Test;
import testUtils.TestMatrix;

import static org.junit.Assert.*;

public class AnchorStampTest {

    /** Build a 3-node chain i--a--j with j grounded, inject +1 A at i. */
    private static Result buildSplitSolve(double rPerM, double L, double x, double Rmin) {
        int i=0, a=1, j=2;
        TestMatrix tm = new TestMatrix(3);
        AnchorStamp.AdmittanceMatrix Ya = tm::add;
        AnchorStamp.CurrentVector    Ja = tm::add;

        // Split resistors i--a and a--j
        AnchorStamp.stampAnchorSplit(Ya, i, a, j, rPerM, L, x, Rmin);

        // Ground node j
        tm.ground(j);

        // Inject +1 A at node i (current into network, ground is sink)
        Ja.add(i, +1.0);

        double[] V = tm.solve();
        return new Result(V[i], V[a], V[j], V);
    }

    private static class Result {
        final double Vi, Va, Vj; final double[] V;
        Result(double Vi, double Va, double Vj, double[] V){ this.Vi=Vi; this.Va=Va; this.Vj=Vj; this.V=V; }
    }

    @Test
    public void resistanceInvariant_sweepX() {
        double rPerM = 0.01;   // Ohm/m
        double L     = 100.0;  // m
        double Rtot  = rPerM * L; // 1.0 Ohm
        double Rmin  = 1e-6;

        double[] xs = {0.0, 0.1*L, 0.5*L, 0.9*L, L};
        for (double x : xs) {
            Result res = buildSplitSolve(rPerM, L, x, Rmin);
            // With +1 A at i and j grounded, V_i ~= total series R
            assertEquals("Total resistance preserved at x="+x, Rtot, res.Vi, 1e-6);
        }
    }

    @Test
    public void endpointRobustness_Rmin() {
        double rPerM = 0.01; double L = 100.0; double Rtot = rPerM * L;
        // make Rmin visible to tolerance
        double Rmin = 1e-4;

        Result nearLeft  = buildSplitSolve(rPerM, L, 0.0, L>0 ? Rmin : 1e-6);
        Result nearRight = buildSplitSolve(rPerM, L, L,   L>0 ? Rmin : 1e-6);

        // Expect small deviation bounded by Rmin
        assertEquals("Left endpoint within tolerance", Rtot, nearLeft.Vi,  5e-4);
        assertEquals("Right endpoint within tolerance", Rtot, nearRight.Vi, 5e-4);
    }

    @Test
    public void equivalence_vs_unsplit() {
        int i=0, j=1;
        double rPerM=0.01, L=100.0, Rtot=rPerM*L, Rmin=1e-6;

        // Unsplit: single resistor i--j
        TestMatrix ref = new TestMatrix(2);
        AnchorStamp.stampResistor(ref::add, i, j, 1.0/Rtot);
        ref.ground(j);
        ref.add(i, +1.0);
        double[] Vref = ref.solve();
        double Vi_ref = Vref[i];

        // Split at x = L/2
        Result split = buildSplitSolve(rPerM, L, 0.5*L, Rmin);

        assertEquals("Split equals unsplit at DC", Vi_ref, split.Vi, 1e-6);
    }

    @Test
    public void nortonShunt_monotone() {
        // Show that adding Gload at anchor lowers Va (monotone effect)
        int i=0, a=1, j=2;
        double rPerM=0.01, L=100.0, Rmin=1e-6, x=0.5*L;

        TestMatrix tm1 = new TestMatrix(3);
        AnchorStamp.stampAnchorSplit(tm1::add, i, a, j, rPerM, L, x, Rmin);
        tm1.ground(j);
        tm1.add(i, +1.0);
        double Va0 = tm1.solve()[a];

        TestMatrix tm2 = new TestMatrix(3);
        AnchorStamp.stampAnchorSplit(tm2::add, i, a, j, rPerM, L, x, Rmin);
        tm2.ground(j);
        tm2.add(i, +1.0);
        // add shunt Gload at anchor
        tm2.add(a, a, 1.0/10.0); // Rload=10 Ohm
        double Va1 = tm2.solve()[a];

        assertTrue("Anchor voltage decreases with shunt load", Va1 < Va0);
    }

    @Test
    public void migrationContinuity_twoEdges_smallDelta() {
        // i--(edge1)--j--(edge2)--k ; anchor moves from end of edge1 to start of edge2
        int i=0, a=1, j=2, k=3;
        double rPerM=0.01, L=100.0, Rmin=1e-6, delta=1e-3;

        // Case A: x = L - delta on edge i--j
        TestMatrix tA = new TestMatrix(4);
        AnchorStamp.stampAnchorSplit(tA::add, i, a, j, rPerM, L, L-delta, Rmin);
        // Also add edge j--k as intact for continuity of network
        AnchorStamp.stampResistor(tA::add, j, k, 1.0/(rPerM*L));
        tA.ground(k);
        tA.add(i, +1.0);
        double VaA = tA.solve()[a];

        // Case B: migrated — x = delta on edge j--k (same anchor node id)
        TestMatrix tB = new TestMatrix(4);
        AnchorStamp.stampResistor(tB::add, i, j, 1.0/(rPerM*L)); // now i--j intact
        AnchorStamp.stampAnchorSplit(tB::add, j, a, k, rPerM, L, delta, Rmin);
        tB.ground(k);
        tB.add(i, +1.0);
        double VaB = tB.solve()[a];

        assertEquals("Anchor voltage is continuous across migration (small delta)", VaA, VaB, 1e-3);
    }
}
