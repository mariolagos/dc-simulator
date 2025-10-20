package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;

import java.util.*;

/**
 * Finds connected components ("islands") in the current DC net,
 * given present voltages V so we can decide if diode feeders are ON.
 *
 * Edges used for connectivity:
 *  - All lines are always ON edges.
 *  - A substation (feeder) is an ON edge iff:
 *      allowBackfeed == true   OR
 *      (Va - Vb) <= E + eps    (forward-biased diode)
 *
 * Result also aggregates per-component meta: any active feeder, any backfeed allowed.
 */
public final class DcIslands {

    private DcIslands() {}

    public static final class Result {
        /** number of components (0..components-1) */
        public final int components;
        /** component id for each node (index by compact node index 0..n-1) */
        public final int[] compOfNode;  // public on purpose (tests use it)
        /** per-component: at least one feeder is forward-active */
        public final boolean[] compHasActiveSubstation;
        /** per-component: at least one feeder allows backfeed */
        public final boolean[] compBackfeedAllowed;
        /** which component contains the ground node */
        public final int groundComponent;

        public Result(int components,
                      int[] compOfNode,
                      boolean[] compHasActiveSubstation,
                      boolean[] compBackfeedAllowed,
                      int groundComponent) {
            this.components = components;
            this.compOfNode = compOfNode;
            this.compHasActiveSubstation = compHasActiveSubstation;
            this.compBackfeedAllowed = compBackfeedAllowed;
            this.groundComponent = groundComponent;
        }

        /** convenience for tests that still call like a method */
        public boolean backfeedAllowedByComp(int c) {
            return (c >= 0 && c < compBackfeedAllowed.length) && compBackfeedAllowed[c];
        }
    }

    /** Decide if a feeder (substation) is an ON edge at V. */
    private static boolean feederActive(SubstationData ss, RealVector V, double eps) {
        final double Va = V.getEntry(ss.a());
        final double Vb = V.getEntry(ss.b());
        final double dV = Va - Vb;
        // Backfeed-allowed is always an ON edge; diode is ON only when forward-biased.
        return ss.allowBackfeed() || (dV <= ss.emf_V() + eps);
    }

    /** Union-Find helpers */
    private static int find(int[] p, int x) {
        int r = x;
        while (p[r] != r) r = p[r];
        // path compression
        while (p[x] != x) { int nx = p[x]; p[x] = r; x = nx; }
        return r;
    }
    private static void union(int[] p, int[] r, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra == rb) return;
        if (r[ra] < r[rb])      p[ra] = rb;
        else if (r[rb] < r[ra]) p[rb] = ra;
        else { p[rb] = ra; r[ra]++; }
    }

    /** Main: build islands based on ON edges (lines always; feeders conditionally). */
    public static Result findIslands(DcNet net, RealVector V, double eps) {
        final int n = net.n();
        final int g = net.groundIndex();

        // 1) Union-Find init
        final int[] parent = new int[n];
        final int[] rank   = new int[n];
        for (int i = 0; i < n; i++) { parent[i] = i; rank[i] = 0; }

        // 2) Lines are always ON edges
        for (LineData L : net.lines()) {
            union(parent, rank, L.a(), L.b());
        }

        // 3) Feeders (conditionally ON)
        final List<SubstationData> activeFeeders = new ArrayList<>();
        final List<SubstationData> allFeeders    = new ArrayList<>(net.substations());
        for (SubstationData ss : allFeeders) {
            if (feederActive(ss, V, eps)) {
                union(parent, rank, ss.a(), ss.b());
                activeFeeders.add(ss);
            }
        }

        // 4) Map roots to compact component ids 0..C-1
        final Map<Integer,Integer> compMap = new HashMap<>();
        int compCnt = 0;
        final int[] compOfNode = new int[n];
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            Integer cid = compMap.get(root);
            if (cid == null) { cid = compCnt++; compMap.put(root, cid); }
            compOfNode[i] = cid;
        }

        // 5) Per-component metadata
        final boolean[] compHasActive = new boolean[compCnt];
        final boolean[] compBackfeed  = new boolean[compCnt];

        for (SubstationData ss : allFeeders) {
            final int ca = compOfNode[ss.a()];
            final int cb = compOfNode[ss.b()];
            if (ca == cb) {
                if (feederActive(ss, V, eps)) compHasActive[ca] = true;
                if (ss.allowBackfeed())       compBackfeed[ca]  = true;
            }
        }

        final int groundComp = compOfNode[g];
        return new Result(compCnt, compOfNode, compHasActive, compBackfeed, groundComp);
    }

    /** Count components excluding the one that contains ground. */
    public static int countNonGroundComponents(DcNet net, Result isl) {
        final int g = net.groundIndex();
        final int groundComp = isl.compOfNode[g];
        final boolean[] present = new boolean[isl.components];
        for (int i = 0; i < net.n(); i++) {
            if (i == g) continue;
            present[isl.compOfNode[i]] = true;
        }
        int k = 0;
        for (int c = 0; c < isl.components; c++) if (c != groundComp && present[c]) k++;
        return k;
    }

    /** Per-component motor-enable (true if that component has any forward-active feeder). */
    public static boolean[] compHasActiveSubstation(DcNet net, RealVector V, Result isl, double eps) {
        final boolean[] res = new boolean[isl.components];
        System.arraycopy(isl.compHasActiveSubstation, 0, res, 0, isl.components);
        return res;
    }
}
