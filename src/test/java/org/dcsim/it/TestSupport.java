package org.dcsim.it;

import org.dcsim.electric.*;
import org.dcsim.math.Real;

public final class TestSupport {

    private TestSupport() {
    }

    /**
     * Bygg ett enkelt 3-SS-nät med en linje mellan varje station (1.5 km).
     */
    public static GridModel<Real> buildMiniGrid(double emfV, double rInt, boolean allowBackfeed) {
        int grdId = 0;
        int nd0Id = 1;
        int ankId = 99;
        int nd1Id = 2;
        int nd2Id = 3;
        Node GRD = new Node(grdId, new Real(0.), "0 0+100");
        Node ND0 = new Node(nd0Id, new Real(0.), "0 0+000");
        Node ND1 = new Node(nd1Id, new Real(0.), "0 1+500");
//        Node ND2 = new Node(nd2Id, new Real(0.), "3 0+000");
        Node ANK = new Node(ankId, new Real(0.), "0 0+200");
        GridModel<Real> m = new GridModel<>(grdId);
        m.setGroundNodeId(GRD);

        // noder
        m.addNode(GRD); // GND
        m.addNode(ND0);
        m.addNode(ND1);
        m.addNode(ANK);

        // linjer (ohm/km antas redan till Real i Line)
        m.addDevice(new Line(nd0Id, ankId, Real.fromDouble(0.1), "L0", "u", 1500));
        m.addDevice(new Line(ankId, nd1Id, Real.fromDouble(0.2), "L1", "u", 1500));

        // stationer
        m.addDevice(new Substation("SS0", nd0Id, grdId, grdId, Real.fromDouble(emfV), Real.fromDouble(rInt)));
        m.addDevice(new Substation("SS1", nd1Id, grdId, grdId, Real.fromDouble(emfV), Real.fromDouble(rInt)));

        // Lägg alltid till T1 på mittnoden
        if (!m.getDeviceIds().contains("T1")) {
            TrainLoad t1 = new TrainLoad("T1", ankId, grdId);
            m.addDevice(t1);
        }
        return m;
    }

    /**
     * Lägg in ett TrainLoad på noden.
     */
    public static TrainLoad addTrain(GridModel<Real> m,
                                     String id,
                                     int nodeId,
                                     int grdId,
                                     double cutoffV,
                                     double maxV,
                                     double maxA) {
        TrainLoad tl = new TrainLoad(id, grdId, nodeId);
        tl.setCutoffVoltage(Real.fromDouble(cutoffV));
        tl.setMaxVoltage(Real.fromDouble(maxV));
        tl.setMaxCurrent(Real.fromDouble(maxA));
        m.addDevice(tl);
        return tl;
    }

    /**
     * Kör en steglösning och returnerar GridResult.
     */
    public static GridResult solveOnce(GridModel<Real> m, double tSec, int step) {
        DcElectricSolver solver = new DcElectricSolver();
        return solver.solve(m, tSec, step);
    }

    /**
     * Hämta nodspänning (double) med säker fallback.
     */
    public static double v(GridResult r, int nodeId) {
        Real rv = r.getLatestNodeVoltage(nodeId);
        return (rv != null) ? rv.asDouble() : 0.0;
    }

    /**
     * Hämta device-effekt (double) med säker fallback.
     */
    public static double p(GridResult r, String devId) {
        Real rp = r.getLatestDevicePower(devId);
        return (rp != null) ? rp.asDouble() : 0.0;
    }

    /**
     * Summera device-effekt för en lista.
     */
    public static double sumP(GridResult r, String... devIds) {
        double s = 0.0;
        for (String id : devIds) s += p(r, id);
        return s;
    }
}
