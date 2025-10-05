package org.dcsim.solver.scenario;

import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.fixtures.NetFixtures;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.dcsim.solver.fixtures.NetFixtures.*;
import static org.junit.Assert.assertEquals;

/**
 * Scenario-style tests that build networks from NetFixtures (no external files).
 * Trains are connected bus->ground (2->0, 3->0) and feeders are diodes unless
 * explicitly enabled for backfeed.
 */
public class DiodeRegenScenarioTests {

    // Tolerances (in watts)
    private static final double TOL_GRID_W = 2_500.0; // grid absorption allowance
    private static final double TOL_ABS_W  = 2_500.0; // absorption near target

    @Test
    public void threeSubs_twoTrains_balanced_diode() {
        // Diode feeders (no backfeed), regen at 2->0, motor at 3->0, equal magnitudes.
        final double P = 100_000.0; // 100 kW
        DcNet net = NetFixtures.regenAt2_motorAt3(P);

        RealVector V = DcIterativeSolver.solveVoltages(net);
        double pGrid = NetFixtures.gridAbsorptionW(net, V);

        // In pure diode mode, the feeders should neither source nor sink (beyond leakage).
        // Balanced motor/regen should make substation absorption ~ 0 W.
        assertEquals("Grid absorption ≈ 0 in balanced motor/regen with diode",
                0.0, pGrid, TOL_GRID_W);
    }

    @Test
    public void threeSubs_oneRegen_unmatched_diode() {
        // Diode feeders (no backfeed), single regen train -> no receptivity.
        // All regen must go to brake; grid ≈ 0 W.
        DcNet net = NetFixtures.oneRegenAt2toGround(100_000.0);

        RealVector V = DcIterativeSolver.solveVoltages(net);
        double pGrid = NetFixtures.gridAbsorptionW(net, V);

        assertEquals("No receptivity: grid absorption ≈ 0 (regen goes to brake)",
                0.0, pGrid, TOL_GRID_W);
    }

    @Test
    public void oneRegen_withBackfeed_absorbedByGrid() {
        // Same backbone but backfeed enabled on feeders;
        // single regen train should be absorbed by the grid (≈ -P).
        final double Pregen = 100_000.0; // 100 kW

        // Build a backbone with allowBackfeed=true
        DcNet base = NetFixtures.threeBusBackbone(V_NOM, RINT, R12, R23, /*allowBackfeed=*/true);

        // Add one regen train at 2->0
        DcNet net = withTrains(base, new TrainData("Tregen", 2, 0, -Pregen, I_MAX_HI, V_MIN, V_MAX));

        RealVector V = DcIterativeSolver.solveVoltages(net);
        double pGrid = NetFixtures.gridAbsorptionW(net, V);

        // Convention: substation power (sum) negative means absorption from line into grid.
        assertEquals("Absorbed power near request", -Pregen, pGrid, TOL_ABS_W);
    }

    // --- small utility to extend an existing net with extra trains ---
    private static DcNet withTrains(DcNet net, TrainData... extra) {
        List<TrainData> ts = new ArrayList<>(net.trains);
        for (TrainData t : extra) ts.add(t);
        return new DcNet(
                net.n,
                net.groundIndex,
                net.nodeIds,
                net.indexOfNodeId,
                net.lines,
                net.substations,
                List.copyOf(ts)
        );
    }
}
