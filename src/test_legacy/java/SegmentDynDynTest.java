// SegmentDynDynTest.java
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.dcsim.electric.SegmentStamp;
import org.dcsim.electric.SegmentStamp.Boundary;
import testUtils.TestMatrix;

public class SegmentDynDynTest {

    @Test
    public void twoTrainsInsideInterval_createDynDynLink_andTotalRPreserved() {
        // Noder: L(0)=immutable vänster, A(1)=tåg1, B(2)=tåg2, R(3)=immutable höger
        final int L=0, A=1, B=2, R=3;
        final int n = 4;

        double Lm = 100.0;   // m
        double Rtot = 1.0;   // Ω total för hela basintervallet
        double Rmin = 1e-6;

        // Boundaries: L@0, A@30m, B@60m, R@100m
        List<Boundary> Bnds = Arrays.asList(
                new Boundary(L, 0.0),
                new Boundary(A, 30.0),
                new Boundary(B, 60.0),
                new Boundary(R, Lm)
        );

        TestMatrix tm = new TestMatrix(n);
        SegmentStamp.stampByBoundaries(tm::add, Rtot, Lm, Bnds, Rmin);

        // Ground R, injicera 1 A i L → ΔV(L→R) ≈ Rtot
        tm.ground(R);
        tm.add(L, +1.0);
        double[] V = tm.solve();

        assertEquals("Total R preserved at 1A", Rtot, V[L], 1e-6);

        // Spänningssteg ska följa proportioner: 30/100, 30/100, 40/100 av 1.0 V
        double dVL_A = V[L] - V[A];   // ~0.3 V
        double dVA_B = V[A] - V[B];   // ~0.3 V
        double dVB_R = V[B] - V[R];   // ~0.4 V

        assertEquals(0.3, dVL_A, 1e-6);
        assertEquals(0.3, dVA_B, 1e-6);
        assertEquals(0.4, dVB_R, 1e-6);
    }
}
