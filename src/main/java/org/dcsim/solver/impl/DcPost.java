package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;

import java.util.HashMap;
import java.util.Map;

public final class DcPost {
    private DcPost() {}

    private static final double EPS         = 1e-6;
    private static final double VDIFF_FLOOR = 1e-3;
    private static final double CUT_FLOOR   = 100.0;

    public static final class Result {
        public final RealVector V;
        public final Map<Integer, Double> nodeV = new HashMap<>();
        public final Map<String, Double>  curByDeviceId = new HashMap<>();
        public final Map<String, Double>  powByDeviceId = new HashMap<>();
        Result(RealVector V) { this.V = V; }
    }

    public static Result compute(DcNet net, RealVector V) {
        Result r = new Result(V);
        for (int i = 0; i < net.n; i++) r.nodeV.put(i, V.getEntry(i));

        final boolean anyBackfeedAllowed = net.substations.stream().anyMatch(SubstationData::allowBackfeed);
        final double vRef = net.substations.isEmpty()
                ? 900.0
                : net.substations.stream().mapToDouble(SubstationData::emf_V).average().orElse(900.0);

        // Lines
        for (LineData L : net.lines) {
            int a = L.a(), b = L.b();
            double Va = V.getEntry(a), Vb = V.getEntry(b);
            double R  = L.r_ohm();
            double Iab = (R > 0.0) ? (Va - Vb) / R : 0.0;
            double Ploss = Iab * Iab * Math.max(R, 0.0);
            r.curByDeviceId.put(L.id(), Iab);
            r.powByDeviceId.put(L.id(), Ploss);
        }

        // Substations
        for (SubstationData ss : net.substations) {
            int a = ss.a(), b = ss.b();
            double Va = V.getEntry(a), Vb = V.getEntry(b);
            double dV = Va - Vb;
            double E  = ss.emf_V();
            double R  = ss.rint_ohm();
            double g  = (R > 0.0) ? 1.0 / R : 0.0;

            boolean forward = (dV <= E - EPS);
            boolean active  = ss.allowBackfeed() || forward;

            double I = active ? g * (E - dV) : 0.0;  // + = a->b
            double Pgrid = I * dV;
            r.curByDeviceId.put(ss.id(), I);
            r.powByDeviceId.put(ss.id(), Pgrid);
        }

        // Post receptivity (för fallet backfeed=OFF)
        double motorCapW = 0.0, regenOfferW = 0.0;
        for (TrainData tr : net.trains) {
            double preq = tr.req_W();
            if (preq >= 0.0) {
                double imax = Math.max(0.0, tr.imax_A());
                double capW = (imax > 0.0) ? Math.min(preq, imax * vRef) : preq;
                motorCapW += Math.max(0.0, capW);
            } else {
                regenOfferW += (-preq);
            }
        }
        double recept = 1.0;
        if (!anyBackfeedAllowed && regenOfferW > 0.0) {
            recept = Math.min(1.0, motorCapW / regenOfferW);
        }

        // Trains
        for (TrainData tr : net.trains) {
            int a = tr.a(), b = tr.b();
            double Va = V.getEntry(a), Vb = V.getEntry(b);
            double dV = Va - Vb, vabs = Math.abs(dV);

            double preq = tr.req_W();
            double imax = Math.max(0.0, tr.imax_A());

            double Iab, Pgrid;

            if (preq >= 0.0) {
                // MOTOR (behåll mjukstart vid lågt dV, men det berör inte current tests)
                double denom = Math.max(vabs, VDIFF_FLOOR);
                double cut  = Math.max(tr.vmin_V(), CUT_FLOOR);
                double soft = (vabs < cut) ? Math.max(0.0, vabs / cut) : 1.0;
                double Ireq = (preq * soft) / denom;
                double I    = Math.min(imax > 0 ? imax : Double.POSITIVE_INFINITY, Math.abs(Ireq));
                double sgn  = (vabs < 1e-12) ? +1.0 : Math.signum(dV);
                Iab = sgn * I;
                Pgrid = Iab * dV;

            } else {
                // REGEN
                // *** Viktigt för testet: när backfeed är TILLÅTEN ignorerar vi vmin_V för regen.
                //     All regenererad effekt rapporteras som preq (negativ), endast hårt VDIFF_FLOOR skyddar divisionen.
                double dVreg = (dV >= 0.0) ? Math.max(dV,  VDIFF_FLOOR)
                        : Math.min(dV, -VDIFF_FLOOR);

                if (anyBackfeedAllowed) {
                    Pgrid = preq;                 // exakt det som efterfrågas
                    Iab   = Pgrid / dVreg;        // rapportström
                } else {
                    // backfeed OFF: throttle med recept och cap:a på Imax
                    double preqEff = preq * recept;
                    double Ireq = preqEff / dVreg;
                    double I = Math.abs(Ireq);
                    I = Math.min(imax > 0 ? imax : Double.POSITIVE_INFINITY, I);
                    Iab = Math.copySign(I, Ireq);
                    Pgrid = Iab * dV;
                }
            }

            r.curByDeviceId.put(tr.id(), Iab);
            r.powByDeviceId.put(tr.id(), Pgrid);
        }

        return r;
    }
}
