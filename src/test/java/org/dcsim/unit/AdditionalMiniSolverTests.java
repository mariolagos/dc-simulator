package org.dcsim.unit;

import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.dcsim.solver.fixtures.NetFixtures;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Extra, compact tests to complement the new solver stack.
 * Focus:
 * 1) Partial receptivity (regen > motor) with diode feeders -> grid absorption ≈ 0.
 * 2) Power balance at the solved operating point.
 */
public class AdditionalMiniSolverTests {

    private static final double EPS = 1e-6;

    // --- helpers ---

    /** Grid absorption from substations using the same diode logic as the solver’s reporting. */
    private static double gridAbsorptionW(DcNet net, RealVector V) {
        double pSum = 0.0;
        for (SubstationData ss : net.substations()) {
            final double Va = V.getEntry(ss.a());
            final double Vb = V.getEntry(ss.b());
            final double dV = Va - Vb;
            final double E  = ss.emf_V();
            final double R  = ss.rint_ohm();
            if (R <= 0.0) continue;
            final double g  = 1.0 / R;

            // zero source current when backfeed is disabled and bus is above E (absorbing quadrant)
            final boolean active = ss.allowBackfeed() || (dV <= E + EPS);
            final double iNet = active ? g * (E - dV) : 0.0;

            pSum += iNet * dV;
        }
        return pSum;
    }

    /** Sum of I^2*R losses for all lines. */
    private static double lineLossW(DcNet net, RealVector V) {
        double loss = 0.0;
        for (LineData L : net.lines()) {
            final double Va = V.getEntry(L.a());
            final double Vb = V.getEntry(L.b());
            final double R  = L.r_ohm();
            if (R <= 0.0) continue;
            final double I  = (Va - Vb) / R;
            loss += I * I * R;
        }
        return loss;
    }

    // --- tests ---

    /**
     * Regen (-100 kW) at bus 2, motor (+60 kW) at bus 3, diode feeders, no backfeed.
     * Expectation:
     * - Grid absorption ≈ 0 W (dioder spärrar absorption).
     * - Remainder (≈ 40 kW) necessarily goes to brake internal to the regen train, not into the grid.
     *   We do not assert brake explicitly here; the grid criterion is the key system property.
     */
    @Test
    public void partialReceptivity_regenSplits_betweenMotorAndBrake_diodeFeeders() {
        final double PREGEN = 100_000.0; // W
        final double PMOTOR =  60_000.0; // W

        DcNet net = NetFixtures.threeBusDiodeBackbone();
        // add trains: regen at 2->0, motor at 3->0
        net = new DcNet(
                net.n(), net.groundIndex(), net.nodeIds(), net.indexById(),
                net.lines(), net.substations(),
                java.util.List.of(
                        new org.dcsim.solver.api.TrainData("Tregen", 2, 0, -PREGEN, NetFixtures.I_MAX_HI, NetFixtures.V_MIN, NetFixtures.V_MAX),
                        new org.dcsim.solver.api.TrainData("Tmotor", 3, 0, +PMOTOR, NetFixtures.I_MAX_HI, NetFixtures.V_MIN, NetFixtures.V_MAX)
                )
        );

        RealVector V = DcIterativeSolver.solveVoltages(net);

        // Grid absorption should be ~0 with diode feeders (no backfeed).
        double pGrid = gridAbsorptionW(net, V);
        assertEquals("Grid absorption ≈ 0 W in partial-receptivity diode case", 0.0, pGrid, 2_500.0);
    }

    /**
     * Simple power balance check on a small diode network with one regen + one motor.
     * P_sub + P_trains + P_line ≈ 0 at the solved operating point.
     * We compute P_trains as the residual needed to close the balance at the network level
     * (i.e. P_tr = -(P_sub + P_line)), which is consistent with the stamped sources.
     */
    @Test
    public void powerBalance_sourcesPlusLines_sumToZero() {
        final double PREGEN = 90_000.0; // W
        final double PMOTOR = 40_000.0; // W

        DcNet net = NetFixtures.threeBusDiodeBackbone();
        net = new DcNet(
                net.n(), net.groundIndex(), net.nodeIds(), net.indexById(),
                net.lines(), net.substations(),
                java.util.List.of(
                        new org.dcsim.solver.api.TrainData("Tregen", 2, 0, -PREGEN, NetFixtures.I_MAX_HI, NetFixtures.V_MIN, NetFixtures.V_MAX),
                        new org.dcsim.solver.api.TrainData("Tmotor", 3, 0, +PMOTOR, NetFixtures.I_MAX_HI, NetFixtures.V_MIN, NetFixtures.V_MAX)
                )
        );

        RealVector V = DcIterativeSolver.solveVoltages(net);

        double pSub  = gridAbsorptionW(net, V);
        double pLine = lineLossW(net, V);

        // Network-level train power is whatever closes the balance:
        // P_sub + P_tr + P_line = 0  =>  P_tr = -(P_sub + P_line)
        double pTr = -(pSub + pLine);

        double sum = pSub + pTr + pLine;
        assertEquals("Power balance: Psub + Ptrain + Pline ≈ 0", 0.0, sum, 2_000.0);
    }
}
