package org.dcsim.unit;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.fixtures.NetFixtures;
import org.dcsim.solver.impl.DcIslands;
import org.junit.Test;

import static org.junit.Assert.*;

public class IslandsPureTopologyTests {
    private static final double EPS = 1e-6;

    @Test
    public void diodeAboveE_splitsIntoTwoIslands() {
        // Tre bussar med dioder. Vi matar ett V som tvingar båda SS att vara "blocked".
        DcNet net = NetFixtures.threeBusBackboneBackfeed(/*emfV*/800.0, /*rint*/0.01, /*r12*/0.1, /*r23*/0.1, /*allowBackfeed*/false);

        // Konstruera ett V som ligger över E på bussarna så dioder spärrar (dV > E)
        RealVector V = new ArrayRealVector(net.n());
        V.setEntry(0, 0.0);      // ground
        V.setEntry(1, 900.0);    // > E => SS1 blocked
        V.setEntry(2, 900.0);
        V.setEntry(3, 900.0);    // > E => SS2 blocked

        DcIslands.Result isl = DcIslands.findIslands(net, V, EPS);

        // Förväntan: linjer 1-2-3 binder ihop de tre bussarna i en ö, GND (0) blir egen ö (ingen ledande SS).
        assertEquals("Expected two islands (bussar + ground)", 2, isl.components);
        // Buss 1 och 3 ska ligga i samma komponent
        assertEquals(isl.compOfNode[1], isl.compOfNode[3]);
        // Ground är annan
        assertNotEquals(isl.compOfNode[1], isl.compOfNode[0]);
    }

    @Test
    public void forwardBiased_mergesToSingleIsland() {
        DcNet net = NetFixtures.threeBusBackboneBackfeed(800.0, 0.01, 0.1, 0.1, false);

        // Lägg bussarna under E så dioder leder (forward)
        RealVector V = new ArrayRealVector(net.n());
        V.setEntry(0, 0.0);
        V.setEntry(1, 700.0); // < E => SS1 forward
        V.setEntry(2, 700.0);
        V.setEntry(3, 700.0); // < E => SS2 forward

        DcIslands.Result isl = DcIslands.findIslands(net, V, EPS);

        // Nu binder SS1 och SS2 ner allt till ground => en enda ö
        assertEquals("Expected single island when both feeders forward-bias to ground", 1, isl.components);
    }
}
