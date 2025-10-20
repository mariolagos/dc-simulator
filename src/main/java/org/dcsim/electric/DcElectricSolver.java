package org.dcsim.electric;

import org.apache.commons.math3.linear.*;
import org.dcsim.math.Real;

import java.util.*;
import java.util.function.Consumer;

public class DcElectricSolver implements ElectricSolver {

    static {
        System.out.println("[WHERE] DcElectricSolver loaded from: " +
                DcElectricSolver.class.getProtectionDomain().getCodeSource().getLocation());
    }

    // ---- numerics ----
    private static final int    MAX_POWER_ITERS = 10;
    private static final double SOLVE_TOL       = 1e-9;
    private static final double VDIFF_FLOOR     = 50.0;  // min |ΔV| used in regen I = P/ΔV (stability)
    private static final double SEED_EPS        = 1e-6;
    private static final double G_EPS           = 1e-9;  // very weak shunt to ground on all non-ground nodes
    private static final double EPS             = 1e-6;  // comparison tolerance

    // optional quick diag
    private static final boolean LOG_SS_AFTER_SOLVE = false;

    // --- typed view over devices to avoid raw -> Object issues ---
    @SuppressWarnings("unchecked")
    private static List<Device<Real>> devices(GridModel<Real> model) {
        return (List<Device<Real>>) (List<?>) model.getDevices();
    }

// GridModel model;  // if you can, prefer: GridModel<Real> model

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void primeSubstationGuesses(
            GridModel model,
            java.util.Map<Object, Integer> idx,                    // use your actual key type
            org.apache.commons.math3.linear.RealVector V
    ) {
        // give substation bus nodes a sensible initial guess (slightly below E)
        for (Object o : model.getDevices()) {                  // raw-safe iteration
            Device<Real> d = (Device<Real>) o;                 // cast once per element

            if (d instanceof Substation) {                    // Substation is NON-generic
                Substation ss = (Substation) d;               // <-- plain Substation
                Integer ai = idx.get(ss.getFromNode());
                if (ai != null) {
                    double emf = ss.getEmf() instanceof org.dcsim.math.Real
                            ? ((org.dcsim.math.Real) ss.getEmf()).asDouble()
                            : ss.getEmf().asDouble();
                    V.setEntry(ai, emf - 1e-3);
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void forEachDevice(GridModel model, Consumer<Device<Real>> fn) {
        for (Object o : model.getDevices()) {
            fn.accept((Device<Real>) o);
        }
    }

    @Override
    public GridResult solve(GridModel model, double timeSec, int timestep) {
        final List<Node> nodes = model.getNodes();
        final int n = nodes.size();

        // nodeId -> compact index
        final Map<Integer, Integer> idx = new HashMap<>(n);
        for (int i = 0; i < n; i++) idx.put(nodes.get(i).getId(), i);

        final Integer gnd = idx.get(model.getGroundNodeId());
        if (gnd == null) throw new IllegalArgumentException("Ground node " + model.getGroundNodeId() + " missing.");

        // Global receptivity gate: if nobody allows backfeed, regen must go to resistor
        final boolean lineCanReceiveRegen = model.isAnyBackfeedAllowed();

        // ===== seed V =====
        RealVector V = new ArrayRealVector(n);
        for (int i = 0; i < n; i++) V.setEntry(i, SEED_EPS);
        V.setEntry(gnd, 0.0);

        // give substation bus nodes a sensible initial guess (slightly below E)
//        for (Device<Real> d : devices(model)) {
//            if (d instanceof Substation ss) {
//                Integer ai = idx.get(ss.getFromNode());
//                if (ai != null) V.setEntry(ai, ss.getEmf().asDouble() - 1e-3);
//            }
//        }
        forEachDevice(model, d -> {
            if (d instanceof Substation) {
                Substation ss = (Substation) d; // Substation är icke-generisk
                Integer ai = idx.get(ss.getFromNode());
                if (ai != null) {
                    // robust emf-uppslag (Real eller annat)
                    double emf = (ss.getEmf() instanceof org.dcsim.math.Real)
                            ? ((org.dcsim.math.Real) ss.getEmf()).asDouble()
                            : (ss.getEmf() instanceof Number
                            ? ((Number) ss.getEmf()).doubleValue()
                            : ss.getEmf().asDouble());
                    V.setEntry(ai, emf - 1e-3);
                }
            }
        });

        // ===== fixed-point like iteration: build G, J and solve G·V = J =====
        RealMatrix G = null;
        RealVector J = null;

        for (int it = 0; it < MAX_POWER_ITERS; it++) {
            G = new Array2DRowRealMatrix(n, n);
            J = new ArrayRealVector(n);

            // 1) Lines: conductance branches
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

            // 2) Substations (diode/backfeed) — pure Norton stamping
            //    Active (allowBackfeed OR forward: Va-Vb <= E + EPS):
            //      - branch-G between a↔b
            //      - source injection +E*g at 'a', -E*g at 'b' (independent of ΔV)
            //    Blocked: tiny leakage only for numerical stability.
            for (Device<Real> d : model.getDevices()) {
                if (d instanceof Substation ss) {
                    Integer a = idx.get(ss.getFromNode());
                    Integer b = idx.get(ss.getToNode());
                    if (a == null || b == null) continue;

                    double R = ss.getInternalResistance().asDouble();
                    if (R <= 0) continue;

                    double g   = 1.0 / R;
                    double E   = ss.getEmf().asDouble();
                    double Va  = V.getEntry(a);
                    double Vb  = V.getEntry(b);
                    double dV  = Va - Vb;

                    boolean forward = (dV <= E + EPS);
                    boolean active  = ss.isAllowBackfeed() || forward;

                    if (active) {
                        // branch G
                        G.addToEntry(a, a, g);
                        G.addToEntry(b, b, g);
                        G.addToEntry(a, b, -g);
                        G.addToEntry(b, a, -g);

                        // Norton current source a→b: +I at 'a', -I at 'b'
                        double Isrc = E * g;
                        J.addToEntry(a, +Isrc);
                        J.addToEntry(b, -Isrc);
                    } else {
                        // blocked — tiny leak keeps matrix well-conditioned
                        final double gLeak = 1e-9;
                        G.addToEntry(a, a, gLeak);
                        G.addToEntry(b, b, gLeak);
                        G.addToEntry(a, b, -gLeak);
                        G.addToEntry(b, a, -gLeak);
                    }
                }
            }

            // 3) Trains: current source a→b, motoring/regen with cut-off + current limit
            for (Device<Real> d : model.getDevices()) {
                if (d instanceof TrainLoad tr) {
                    Integer a = idx.get(tr.getFromNode());
                    Integer b = idx.get(tr.getToNode());
                    if (a == null || b == null) continue;

                    double preq = (tr.getRequestedPower() != null)
                            ? tr.getRequestedPower().asDouble() : 0.0; // W (+ motoring, − braking)

                    double Va = V.getEntry(a);
                    double Vb = V.getEntry(b);
                    double dV = Va - Vb;

                    final double cut  = tr.getCutoffVoltage().asDouble(); // e.g., 850 V
                    final double vmax = tr.getMaxVoltage().asDouble();

                    double Iab; // a→b

                    if (preq >= 0.0) {
                        // --- Motoring (no 50V floor; tiny guard only) ---
                        double dVmot = (Math.abs(dV) < 1e-6)
                                ? Math.copySign(1e-6, (dV == 0 ? 1.0 : dV))
                                : dV;

                        // simple derating near low voltage (separate vmin logic can be added later)
                        double scale = 1.0;
                        if (Math.abs(dV) < cut) {
                            scale = Math.max(0.0, Math.abs(dV) / cut);
                        }
                        double pEff = preq * scale;

                        Iab = pEff / dVmot; // +I a→b

                    } else {
                        // --- Regeneration ---
                        // floor ΔV for numerical stability
                        double dVreg = Math.abs(dV) < VDIFF_FLOOR
                                ? Math.copySign(VDIFF_FLOOR, (dV == 0 ? 1.0 : dV))
                                : dV;

                        double vabs = Math.abs(dVreg);
                        double fracLine = (vabs <= cut) ? 1.0
                                : (vabs >= vmax) ? 0.0
                                : (vmax - vabs) / (vmax - cut);

                        // global receptivity gate
                        if (!lineCanReceiveRegen) fracLine = 0.0;

                        Iab = (preq * fracLine) / dVreg;  // preq<0 ⇒ Iab<0 when any goes to line
                    }

                    // current limit
                    double Imax = tr.getMaxCurrent().asDouble();
                    if (Imax > 0 && Math.abs(Iab) > Imax) Iab = Math.copySign(Imax, Iab);

                    // stamp: current source a→b = -I at 'a', +I at 'b' (injection is into node)
                    J.addToEntry(a, -Iab);
                    J.addToEntry(b, +Iab);
                }
            }

            // 3.5) tiny shunt to ground for all non-ground nodes
            for (int k = 0; k < n; k++) {
                if (k == gnd) continue;
                G.addToEntry(k, k, G_EPS);
            }

            // 4) clamp ground Vg = 0 (zero both row and column)
            for (int r = 0; r < n; r++) G.setEntry(r, gnd, 0.0);
            for (int c = 0; c < n; c++) G.setEntry(gnd, c, 0.0);
            G.setEntry(gnd, gnd, 1.0);
            J.setEntry(gnd, 0.0);

            // 5) solve
            RealVector Vnext = new LUDecomposition(G).getSolver().solve(J);
            double delta = Vnext.subtract(V).getNorm();
            V = Vnext;
            if (delta < SOLVE_TOL) break;
        }

        // ===== build result =====
        GridResult res = new GridResult(G, J);

        // node voltages
        for (int i = 0; i < n; i++) {
            int nodeId = nodes.get(i).getId();
            res.setVoltage(nodeId, Real.fromDouble(V.getEntry(i)));
        }

        // per-device I/P (writer and accounting rely on this)
        for (Device<Real> d : model.getDevices()) {

            if (d instanceof Line line) {
                int aId = line.getFromNode(), bId = line.getToNode();
                double Va = res.getLatestNodeVoltage(aId).asDouble();
                double Vb = res.getLatestNodeVoltage(bId).asDouble();
                double R  = line.getResistance().asDouble();

                double Iab = (R > 0.0) ? (Va - Vb) / R : 0.0;
                double Ploss = Iab * Iab * Math.max(R, 0.0);

                res.setCurrent(line.getId(), Real.fromDouble(Iab));
                res.setPower(line.getId(),   Real.fromDouble(Ploss));

            } else if (d instanceof Substation ss) {
                int aId = ss.getFromNode(), bId = ss.getToNode();
                double Va = res.getLatestNodeVoltage(aId).asDouble();
                double Vb = res.getLatestNodeVoltage(bId).asDouble();
                double E  = ss.getEmf().asDouble();
                double R  = ss.getInternalResistance().asDouble();
                double g  = (R > 0.0) ? 1.0 / R : 0.0;
                double dV = Va - Vb;

                boolean forward = (dV <= E + EPS);
                boolean active  = ss.isAllowBackfeed() || forward;

                double iNet = active ? g * (E - dV) : 0.0;      // b→a positive when feeding
                res.setCurrent(ss.getId(), Real.fromDouble(iNet));
                res.setPower(ss.getId(),   Real.fromDouble(iNet * dV)); // P_out = I * (Va - Vb)

            } else if (d instanceof TrainLoad tr) {
                int aId = tr.getFromNode(), bId = tr.getToNode();
                double Va = res.getLatestNodeVoltage(aId).asDouble();
                double Vb = res.getLatestNodeVoltage(bId).asDouble();
                double dV = Va - Vb;

                final double cut  = tr.getCutoffVoltage().asDouble();
                final double vmax = tr.getMaxVoltage().asDouble();

                double preq = (tr.getRequestedPower() != null) ? tr.getRequestedPower().asDouble() : 0.0;

                double Iab;
                double pBrake = 0.0;

                if (preq >= 0.0) {
                    double dVmot = (Math.abs(dV) < 1e-6)
                            ? Math.copySign(1e-6, (dV == 0 ? 1.0 : dV))
                            : dV;

                    double scale = 1.0;
                    if (Math.abs(dV) < cut) {
                        scale = Math.max(0.0, Math.abs(dV) / cut);
                    }
                    double pEff = preq * scale;

                    Iab = pEff / dVmot;

                } else {
                    double dVreg = Math.abs(dV) < VDIFF_FLOOR
                            ? Math.copySign(VDIFF_FLOOR, (dV == 0 ? 1.0 : dV))
                            : dV;

                    double vabs = Math.abs(dVreg);
                    double fracLine = (vabs <= cut) ? 1.0
                            : (vabs >= vmax) ? 0.0
                            : (vmax - vabs) / (vmax - cut);
                    if (!lineCanReceiveRegen) fracLine = 0.0;

                    Iab    = (preq * fracLine) / dVreg;
                    pBrake = (-preq) * (1.0 - fracLine); // ≥ 0
                }

                double Imax = tr.getMaxCurrent().asDouble();
                if (Imax > 0 && Math.abs(Iab) > Imax) Iab = Math.copySign(Imax, Iab);

                res.setCurrent(tr.getId(),          Real.fromDouble(Iab));
                res.setPower(tr.getId(),            Real.fromDouble(Iab * (Va - Vb))); // network view
                res.setRequestedPower(tr.getId(),   Real.fromDouble(preq));
                res.setPower(tr.getId() + "#brake", Real.fromDouble(pBrake));
            }
        }

        if (LOG_SS_AFTER_SOLVE) {
            for (Device<Real> d : model.getDevices()) {
                if (d instanceof Substation ss) {
                    double Va = res.getLatestNodeVoltage(ss.getFromNode()).asDouble();
                    double Vb = res.getLatestNodeVoltage(ss.getToNode()).asDouble();
                    double dV = Va - Vb;
                    double E  = ss.getEmf().asDouble();
                    System.out.printf(Locale.US,
                            "t=%.3f S[%s]: Va-Vb=%.3fV, E=%.3fV, E-dV=%.3fV, I≈(E-dV)/R%n",
                            timeSec, ss.getId(), dV, E, (E - dV));
                }
            }
        }

        return res;
    }
}
