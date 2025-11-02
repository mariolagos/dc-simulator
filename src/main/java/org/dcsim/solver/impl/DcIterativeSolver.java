package org.dcsim.solver.impl;

import org.apache.commons.math3.FieldElement;
import org.apache.commons.math3.linear.*;
import org.dcsim.electric.ElectricSolver;
import org.dcsim.export.LongTableWriter;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.build.DcSystem;
import org.dcsim.solver.build.MatrixBuilder;

import java.util.Arrays;

import static org.dcsim.solver.impl.DcDebug.log;
import static org.dcsim.solver.impl.DcDebug.line;

public final class DcIterativeSolver {
    // === Long-table hooks (static) ===
    private static LongTableWriter LongWriter = null;
    private static double simTimeSec = Double.NaN;
    private static int sTrainNodeIndex = 4;

    // ==================================


    public DcIterativeSolver() {
    }

    // numerics
    private static final double EPS = 1e-6;
    private static final double RELAX = 1.0;
    private static final double KCL_TOL = 1e-6;
    private static final double DV_TOL = 1e-6;
    private static final int IT_MAX = 50;

    // === Long-table: multi-train + multi-node support ===
    private static final java.util.LinkedHashMap<String,Integer> sTrainNodeById = new java.util.LinkedHashMap<>();
    private static final java.util.LinkedHashMap<String,Integer> sProbeNodesByName = new java.util.LinkedHashMap<>();

    private static final java.util.LinkedHashMap<String,int[]> sProbeBranches = new java.util.LinkedHashMap<>();
    // name -> [i, j], resistans hämtas från RByEdge om du har, annars skickas som extra lista
    private static final java.util.LinkedHashMap<String,Double> sBranchR = new java.util.LinkedHashMap<>();

    public static void clearProbeBranches() {
        sProbeBranches.clear();
        sBranchR.clear();
    }
    public static void setProbeBranch(String name, int i, int j, double R_ohm) {
        if (name == null) return;
        sProbeBranches.put(name, new int[]{i,j});
        sBranchR.put(name, R_ohm);
    }


    public static void clearTrainNodes() { sTrainNodeById.clear(); }
    public static void setTrainNode(String trainId, int nodeIdx) {
        if (trainId != null) sTrainNodeById.put(trainId, nodeIdx);
    }
    public static void clearProbeNodes() { sProbeNodesByName.clear(); }
    public static void setProbeNode(String name, int nodeIdx) {
        if (name != null) sProbeNodesByName.put(name, nodeIdx);
    }

    // NaN/Inf guard
    private static boolean finite(double x) { return !Double.isNaN(x) && !Double.isInfinite(x); }
    /**
     * Backwards-compatible alias some tests still call.
     */
    public static RealVector solve(DcNet net) {
        return solveVoltages(net);
    }

//    private static org.dcsim.export.LongTableWriter longWriter;
    private static int trainNodeIndex = 4; // fast track – samma nod du loggade

    public static void setLongWriter(org.dcsim.export.LongTableWriter w) {
        LongWriter = w;
    }

    public static void setSimTimeSec(double t) {
        simTimeSec = t;
    }

    public static void setTrainNodeIndex(int idx) {
        trainNodeIndex = idx;
    }

    public static double getSimTimeSec() { return simTimeSec; }

    /**
     * Main entry: compute node voltages.
     */
    public static RealVector solveVoltages(DcNet net) {
        final int n = net.n();
        final int g = net.groundIndex();

        RealVector V = new ArrayRealVector(n); // seed 0V
        DcSystem base = null;
        RealMatrix G = null;
        RealVector J = null;

        for (int it = 0; it < IT_MAX; it++) {
            line();
            log("[it=%d] start", it);

            // 1) Base G,J (lines only). MatrixBuilder wants double[] for V.
            base = MatrixBuilder.build(net, (V == null ? null : V.toArray()));
            G = base.G().copy();
            J = base.J().copy();

            // 2) Diode substations
            for (SubstationData ss : net.substations()) {
                DcStamps.stampSubstation(
                        V, G, J,
                        ss.a(), ss.b(),
                        ss.emf_V(), ss.rint_ohm(),
                        ss.allowBackfeed(),
                        /*eps=*/1e-6,
                        /*gLeakDiag=*/1e-12
                );
            }

            // 3) Island analysis (decide motorEnabled per component)
            final DcIslands.Result isl = DcIslands.findIslands(net, V, EPS);
            if (DcDebug.VERBOSE) {
                log("[isl] components=%d", isl.components);
                log("[isl] compHasActiveSubstation=%s", Arrays.toString(isl.compHasActiveSubstation));
                log("[isl] compBackfeedAllowed   =%s", Arrays.toString(isl.compBackfeedAllowed));
            }

            // 4) Trains (vminDefault=0 ⇒ only tr.vmin_V() throttles)
            for (TrainData tr : net.trains()) {
                final int comp = isl.compOfNode[tr.a()];
                final boolean motorEnabled =
                        (comp >= 0 && comp < isl.components) && isl.compHasActiveSubstation[comp];
                if (DcDebug.VERBOSE) {
                    log("[train %s] comp=%d motorEnabled=%s", tr.id(), comp, motorEnabled);
                }

                DcStamps.stampTrain(
                        V, G, J,
                        tr,
                        /*vminDefault=*/0.0,
                        motorEnabled
                );
            }

            // 5) Ground clamp for the global ground
            clampNode(G, J, g, "GROUND");

            // 6) One clamp per island (≠ ground component), prefer b-terminal of a substation
            final int groundComp = isl.compOfNode[g];
            boolean[] clamped = new boolean[n];
            clamped[g] = true;

            for (int c = 0; c < isl.components; c++) {
                if (c == groundComp) continue; // already referenced via global ground
                int rep = chooseIslandClampNode(net, isl, c);
                if (rep >= 0 && !clamped[rep]) {
                    clampNode(G, J, rep, "ISLAND#" + c);
                    clamped[rep] = true;
                }
            }

            // 7) Optional compact dump
            sanityDump(G, J);

            // 8) Solve
            final RealVector Vnext = new LUDecomposition(G).getSolver().solve(J);

            // 9) Residuals + summary
            final double kclInf = G.operate(Vnext).subtract(J).getLInfNorm();
            final double dvInf = Vnext.subtract(V).getLInfNorm();
            final double vmin = min(Vnext);
            final double vmax = max(Vnext);

            log("[it=%d] kclInf=%.3e  dvInf=%.3e  Vmin=%.3f  Vmax=%.3f", it, kclInf, dvInf, vmin, vmax);

            // relax
            V = V.mapMultiply(1.0 - RELAX).add(Vnext.mapMultiply(RELAX));

            if (kclInf < KCL_TOL && dvInf < DV_TOL) {
                log("[it=%d] converged", it);
                break;
            }
        }

        dumpVoltages(V, "final V");

        // --- TEMP NET-PROBE (efter konvergens) ---
        try {
            final int nTrain = net.tryIdxOf(99); // din hårdkodade tågnod
            if (nTrain >= 0 && nTrain < V.getDimension()) {
                double Vt = V.getEntry(nTrain);  // spänning i tågnoden
                double It = J.getEntry(nTrain);  // nodström = rhs-komponent

                // Teckenkonvention:
                // Om J definieras som "injektion till nätet" (vanligt i nodanalys),
                // vill du se positiv last vid motordrift: använd -It.
                double Pt = Vt * (-It);

                System.out.printf("[NET] node=%d V=%.1f V I=%.0f A P=%.0f W%n",
                        nTrain, Vt, -It, Pt);
            } else {
                System.out.printf("[NET] node=%d out of bounds (dim=%d)%n", nTrain, V.getDimension());
            }
        } catch (Throwable t) {
            System.out.println("[NET] probe disabled: " + t);
        }
// --- END PROBE ---

        try {
            org.dcsim.export.LongTableWriter w = LongWriter; // eller DcIterativeSolver.getLongWriter() om du inte har sLongWriter
            if (w != null) {
                final double t = DcIterativeSolver.getSimTimeSec();

                // --- Stationer: S1,S2,S3 (noder 1..3) -> V, I, P ---
                for (int idx = 1; idx <= 3; idx++) {
                    final String name = "S" + idx;
                    if (idx < 0 || idx >= V.getDimension()) continue;
                    final double Vn    = V.getEntry(idx);
                    final double Iinj  = J.getEntry(idx);  // + till nät
                    final double Iload = -Iinj;            // + från nät (last)
                    final double Pn    = Vn * Iload;

                    if (!Double.isNaN(Vn)    && !Double.isInfinite(Vn))    w.signalRow(t, "Node",  name, "V_V", Vn,    "V", "NET", null, null);
                    if (!Double.isNaN(Iload) && !Double.isInfinite(Iload)) w.signalRow(t, "Node",  name, "I_A", Iload, "A", "NET", null, null);
                    if (!Double.isNaN(Pn)    && !Double.isInfinite(Pn))    w.signalRow(t, "Node",  name, "P_W", Pn,    "W", "NET", null, null);
                }

                // --- Tågets nod: T1 på nod 4 -> P_W (faktisk nät-effekt) ---
                final int trainNode = 4; // 3subs1train
                if (trainNode >= 0 && trainNode < V.getDimension()) {
                    final double Vt    = V.getEntry(trainNode);
                    final double Iinj  = J.getEntry(trainNode);
                    final double Iload = -Iinj;
                    final double Pt    = Vt * Iload;
                    if (!Double.isNaN(Pt) && !Double.isInfinite(Pt))
                        w.signalRow(t, "Train", "T1", "P_W", Pt, "W", "NET", null, null);
                }

                for (var e : sProbeBranches.entrySet()) {
                    final String name = e.getKey();
                    final int[] ij = e.getValue();
                    final Double R = sBranchR.get(name);
                    if (ij == null || ij.length != 2 || R == null || !finite(R) || R <= 0) continue;
                    final int i = ij[0], j = ij[1];
                    if (i < 0 || j < 0 || i >= V.getDimension() || j >= V.getDimension()) continue;

                    final double Vi = V.getEntry(i), Vj = V.getEntry(j);
                    final double I  = (Vi - Vj) / R;     // riktning i->j
                    final double Pl = I * I * R;

                    if (finite(I))  w.signalRow(t, "Line", name, "I_A", I,  "A", "NET", null, null);
                    if (finite(Pl)) w.signalRow(t, "Line", name, "P_W", Pl, "W", "NET", null, null);
                }

            }
        } catch (Throwable ignore) { /* inga kast, bara logg */ }

        return V;
    }


    /**
     * Pick a clamp node inside component 'comp'. Prefer a substation b-node; fallback to first node in comp.
     */
    private static int chooseIslandClampNode(DcNet net, DcIslands.Result isl, int comp) {
        // Prefer b-terminal of any substation fully inside this component
        for (SubstationData ss : net.substations()) {
            int a = ss.a(), b = ss.b();
            if (isl.compOfNode[a] == comp && isl.compOfNode[b] == comp) {
                if (DcDebug.VERBOSE) log("[clamp-pick] comp=%d -> ss=%s pick b=%d", comp, ss.id(), b);
                return b;
            }
        }
        // Fallback: first node that belongs to the component
        for (int i = 0; i < isl.compOfNode.length; i++) {
            if (isl.compOfNode[i] == comp) {
                if (DcDebug.VERBOSE) log("[clamp-pick] comp=%d -> fallback node=%d", comp, i);
                return i;
            }
        }
        return -1;
    }

    /**
     * Clamp V[idx]=0 by zeroing row/col and setting G[idx,idx]=1, J[idx]=0 (like ground).
     */
    private static void clampNode(RealMatrix G, RealVector J, int idx, String tag) {
        final int n = G.getRowDimension();
        for (int r = 0; r < n; r++) G.setEntry(r, idx, 0.0);
        for (int c = 0; c < n; c++) G.setEntry(idx, c, 0.0);
        G.setEntry(idx, idx, 1.0);
        J.setEntry(idx, 0.0);
        if (DcDebug.VERBOSE) log("[clamp] node %d <= 0V (%s)", idx, tag);
    }

    private static double min(RealVector v) {
        double m = Double.POSITIVE_INFINITY;
        for (int i = 0; i < v.getDimension(); i++) m = Math.min(m, v.getEntry(i));
        return m;
    }

    private static double max(RealVector v) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < v.getDimension(); i++) m = Math.max(m, v.getEntry(i));
        return m;
    }

    private static void sanityDump(RealMatrix G, RealVector J) {
        if (!DcDebug.VERBOSE) return;
        final int n = G.getRowDimension();
        if (n > 12) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== G ===\n");
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) sb.append(String.format("%14.6e ", G.getEntry(r, c)));
            sb.append("\n");
        }
        sb.append("=== J ===\n");
        for (int r = 0; r < n; r++) sb.append(String.format("%14.6e ", J.getEntry(r)));
        log(sb.toString());
    }

    private static void dumpVoltages(RealVector V, String tag) {
        if (!DcDebug.VERBOSE) return;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(tag).append("] V = {");
        for (int i = 0; i < V.getDimension(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(String.format("%.3f", V.getEntry(i)));
        }
        sb.append("}");
        log(sb.toString());
    }

    public static LongTableWriter getLongWriter() {
        return LongWriter;
    }
}
