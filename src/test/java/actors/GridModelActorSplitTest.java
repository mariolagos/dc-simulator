package org.dcsim.actors;

import org.junit.Test;
import static org.junit.Assert.*;

public class GridModelActorSplitTest {

    private static final double EPS = 1e-9;

    @Test
    public void c1_nonReceptive_allToResistor() {
        double req = 50_000;       // W regen begärd
        double pFromNet = +10_000; // solver: + = motoring, − = regen till nät (här ingen regen)
        double Va = 850;           // V
        double Vmax = 900;         // V
        boolean receptive = false; // nätet tar inte emot

        GridModelActor.BrakeSplit bs =
                GridModelActor.computeBrakeSplit(req, pFromNet, Va, Vmax, receptive);

        assertEquals(0.0, bs.netW, EPS);
        assertEquals(req, bs.resW, EPS);
    }

    @Test
    public void c2_fullyReceptive_allToNet() {
        double req = 50_000;
        double exportedBySolver = 50_000;
        double pFromNet = -exportedBySolver; // − => export till nät
        double Va = 820;
        double Vmax = 900;
        boolean receptive = true;

        GridModelActor.BrakeSplit bs =
                GridModelActor.computeBrakeSplit(req, pFromNet, Va, Vmax, receptive);

        assertEquals(req, bs.netW, EPS);
        assertEquals(0.0, bs.resW, EPS);
    }

    @Test
    public void c3_partialReceptivity_split() {
        double req = 50_000;
        double exportedBySolver = 20_000; // nätet tar emot 20 kW
        double pFromNet = -exportedBySolver;
        double Va = 830;
        double Vmax = 900;
        boolean receptive = true;

        GridModelActor.BrakeSplit bs =
                GridModelActor.computeBrakeSplit(req, pFromNet, Va, Vmax, receptive);

        assertEquals(20_000, bs.netW, EPS);
        assertEquals(30_000, bs.resW, EPS);
    }

    @Test
    public void c4_motoring_noBrake() {
        double req = 0.0;          // ingen broms begärd
        double pFromNet = +80_000; // motoring (import)
        double Va = 840;
        double Vmax = 900;
        boolean receptive = true;

        GridModelActor.BrakeSplit bs =
                GridModelActor.computeBrakeSplit(req, pFromNet, Va, Vmax, receptive);

        assertEquals(0.0, bs.netW, EPS);
        assertEquals(0.0, bs.resW, EPS);
    }

    @Test
    public void d_blockOnVmax_allToResistor() {
        double req = 40_000;
        double exportedBySolver = 40_000; // solver ”skulle” exportera
        double pFromNet = -exportedBySolver;
        double Va = 900.0; // vid/över Vmax => blockera net-regen
        double Vmax = 900.0;
        boolean receptive = true; // spelar ingen roll vid Vmax-klamp

        GridModelActor.BrakeSplit bs =
                GridModelActor.computeBrakeSplit(req, pFromNet, Va, Vmax, receptive);

        assertEquals(0.0, bs.netW, EPS);
        assertEquals(40_000, bs.resW, EPS);
    }
}
