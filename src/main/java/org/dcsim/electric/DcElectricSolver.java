package org.dcsim.electric;

import org.apache.commons.math3.linear.*;
import org.dcsim.math.Real;

import java.util.*;

public class DcElectricSolver implements ElectricSolver {

    // Iterationer för P-beroende last (tåg)
    private static final int MAX_POWER_ITERS = 10;
    private static final double SOLVE_TOL = 1e-9;
    private static final double VDIFF_FLOOR = 50.0;   // min |ΔV| för I = P/ΔV (V)
    private static final double SEED_EPS = 1e-6;

    @Override
    public GridResult solve(GridModel model, double time, int timestep) {
        final List<Node> nodes = model.getNodes();
        final int n = nodes.size();

        // nodeId -> index
        final Map<Integer,Integer> idx = new HashMap<>(n);
        for (int i = 0; i < n; i++) idx.put(nodes.get(i).getId(), i);

        final Integer gnd = idx.get(model.getGroundNodeId());
        if (gnd == null) throw new IllegalArgumentException("Ground node " + model.getGroundNodeId() + " missing.");

        // ===== Seed V =====
        RealVector V = new ArrayRealVector(n);
        for (int i = 0; i < n; i++) V.setEntry(i, SEED_EPS);
        V.setEntry(gnd, 0.0);
        // ge stationernas "to"-noder ett rimligt startvärde
        for (Device<Real> d : model.getDevices()) {
            if (d instanceof Substation ss) {
                Integer ti = idx.get(ss.getToNode());
                if (ti != null) V.setEntry(ti, ss.getEmf().asDouble());
            }
        }

        // ===== Liten fast iteration: bygg G + J(sub)+J(train), lös GV=J =====
        RealMatrix G = null;
        RealVector J = null;
        for (int it = 0; it < MAX_POWER_ITERS; it++) {
            G = new Array2DRowRealMatrix(n, n);
            J = new ArrayRealVector(n);

            // 1) Linjer: konduktansgrenar
            for (Device<Real> d : model.getDevices()) {
                if (d instanceof Line line) {
                    Integer a = idx.get(line.getFromNode());
                    Integer b = idx.get(line.getToNode());
                    if (a == null || b == null) continue;
                    double R = line.getResistance().asDouble();
                    if (R <= 0) continue;
                    double g = 1.0 / R;
                    G.addToEntry(a, a, g);
                    G.addToEntry(b, b, g);
                    G.addToEntry(a, b, -g);
                    G.addToEntry(b, a, -g);
                }
            }

            // 2) Stationer (Thevenin a↔b) som Norton: g mellan a↔b och I=E/R från b→a
            for (Device<Real> d : model.getDevices()) {
                if (d instanceof Substation ss) {
                    Integer a = idx.get(ss.getFromNode());
                    Integer b = idx.get(ss.getToNode());
                    if (a == null || b == null) continue;
                    double R = ss.getInternalResistance().asDouble();
                    if (R <= 0) continue;
                    double g = 1.0 / R;
                    double E = ss.getEmf().asDouble();

                    // konduktansgren
                    G.addToEntry(a, a, g);
                    G.addToEntry(b, b, g);
                    G.addToEntry(a, b, -g);
                    G.addToEntry(b, a, -g);

                    // strömkälla b->a (ger Vb - Va = E i öppet fall)
                    double I = E * g;
                    J.addToEntry(a, -I);
                    J.addToEntry(b, +I);
                }
            }

            // 3) Tåg: strömkällor a->b med I = P_req / ΔV (klampad)
            for (Device<Real> d : model.getDevices()) {
                if (d instanceof TrainLoad tr) {
                    Integer a = idx.get(tr.getFromNode());
                    Integer b = idx.get(tr.getToNode());
                    if (a == null || b == null) continue;
                    double preq = 0.0;
                    Real rP = tr.getRequestedPower();
                    if (rP != null) preq = rP.asDouble(); // W (+ motering, − regen)

                    double Va = V.getEntry(a);
                    double Vb = V.getEntry(b);
                    double dV = Va - Vb;
                    if (Math.abs(dV) < VDIFF_FLOOR) dV = (dV >= 0 ? VDIFF_FLOOR : -VDIFF_FLOOR);

                    double Iab = preq / dV; // A (kan vara −)
                    // nodal injektion: -I i a, +I i b
                    J.addToEntry(a, -Iab);
                    J.addToEntry(b, +Iab);
                }
            }

            // 4) Kläm ground: V_g = 0
            for (int col = 0; col < n; col++) G.setEntry(gnd, col, 0.0);
            G.setEntry(gnd, gnd, 1.0);
            J.setEntry(gnd, 0.0);

            // 5) Lös
            RealVector Vnext = new LUDecomposition(G).getSolver().solve(J);
            double delta = Vnext.subtract(V).getNorm();
            V = Vnext;
            if (delta < SOLVE_TOL) break;
        }

        // ===== Skriv ut resultat =====
        GridResult res = new GridResult(G, J);

        // Nodspänningar
        for (int i = 0; i < n; i++) {
            int nodeId = nodes.get(i).getId();
            res.setVoltage(nodeId, Real.fromDouble(V.getEntry(i)));
        }

        // Enhetsströmmar/effekter
        for (Device<Real> d : model.getDevices()) {
            if (d instanceof Line line) {
                int aId = line.getFromNode();
                int bId = line.getToNode();
                double Va = res.getLatestNodeVoltage(aId).asDouble();
                double Vb = res.getLatestNodeVoltage(bId).asDouble();
                double R = line.getResistance().asDouble();
                double Iab = (R > 0) ? (Va - Vb) / R : 0.0;   // A, från a till b
                double Ploss = Iab * Iab * Math.max(R, 0.0); // W (≥0)
                res.setCurrent(line.getId(), Real.fromDouble(Iab));
                res.setPower(line.getId(), Real.fromDouble(Ploss));

            } else if (d instanceof Substation ss) {
                int aId = ss.getFromNode();
                int bId = ss.getToNode();
                double Va = res.getLatestNodeVoltage(aId).asDouble();
                double Vb = res.getLatestNodeVoltage(bId).asDouble();
                double E  = ss.getEmf().asDouble();
                double R  = ss.getInternalResistance().asDouble();
                double g  = (R > 0) ? 1.0 / R : 0.0;
                double dV = Vb - Va; // (b minus a)

                // Ström enligt Norton: I = g*(E - dV) från b->a
                double Inorton = g * (E - dV);
                res.setCurrent(ss.getId(), Real.fromDouble(-Inorton)); // valfritt tecken; inte kritiskt

                // Effekt levererad till nätet
                double Pnet = g * (E * dV - dV * dV); // >0 matning, <0 regen
                res.setPower(ss.getId(), Real.fromDouble(Pnet));

            } else if (d instanceof TrainLoad train) {
                Real fromV = res.getLatestNodeVoltage(train.getFromNode());
                Real toV   = res.getLatestNodeVoltage(train.getToNode());

                // deltaV = V(to) - V(from)
                Real dv = toV.minus(fromV);

                // computeCurrent använder den begärda effekten (mot +, broms -)
                Real i = train.computeCurrent(fromV, toV);

                // Viktigt: levererad effekt från nätets synvinkel = I * ΔV (med tecken)
                Real p = i.times(dv);

                res.setCurrent(train.getId(), i);
                res.setPower(train.getId(), p);               // <— nu negativ vid broms
                res.setRequestedPower(train.getId(), train.getRequestedPower());
            }
        }

        return res;
    }
}
