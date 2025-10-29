package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.*;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.MatrixBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * Enkel linjär DC-lösare för statiska element (Lines + Substations),
 * med verbose-dumpar och en robust lokal fallback-stämpning av substationer
 * efter MatrixBuilder.build(...).
 *
 * Nu med stöd för SubstationData{id=..., a=..., b=..., emf_V=..., rint_ohm=...}
 * (Theveninkälla mellan a och b -> Norton: g=1/R, I=E/R, stampas mellan a och b).
 */
public final class DcLinearSolver {

    /** Slå på för att skriva G/J innan/efter clamp samt V efter lösning. */
    public static volatile boolean VERBOSE = false;

    private DcLinearSolver() {}

    /** Löser G·V = J för ett givet nät. Vseed kan vara null. */
    public static double[] solveVoltages(DcNet net, double[] Vseed) {
        // 1) Bygg G och J från nätet (+ ev. seed för diodgating i stationer)
        var sys = MatrixBuilder.build(net, Vseed); // använder din befintliga MatrixBuilder
        RealMatrix G = sys.G();
        RealVector J = sys.J();

        final boolean v = VERBOSE || DcDebug.VERBOSE;
        if (v) {
            DcDebug.line();
            DcDebug.log("[DcLinearSolver] Start linear solve...");
            DcDebug.log("[DcLinearSolver] nodes=%d ground=%d", net.n, net.groundIndex());
            DcDebug.dumpMatrix("[DcLinearSolver] G (before substation fallback)", G);
            DcDebug.dumpMatrixOrVector("[DcLinearSolver] J (before substation fallback)", J);
        }

        // 1b) Fallback: om MatrixBuilder inte verkar ha stampat substationer (J≈0),
        // försök stämpla Norton-ekvivalent här.
        if (isZeroVector(J)) {
            int stamped = applySubstationFallbackStamp(G, J, net, v);
            if (v) {
                if (stamped == 0) {
                    DcDebug.log("[DcLinearSolver] Fallback found no stampable substation (missing getters/fields?)");
                } else {
                    DcDebug.log("[DcLinearSolver] Fallback stamped %d substation(s).", stamped);
                }
            }
        }

        if (v) {
            DcDebug.dumpMatrix("[DcLinearSolver] G (before clamp)", G);
            DcDebug.dumpMatrixOrVector("[DcLinearSolver] J (before clamp)", J);
        }

        // 2) Kläm marknoden
        clampGround(G, J, net.groundIndex());

        if (v) {
            DcDebug.dumpMatrix("[DcLinearSolver] G (after clamp)", G);
            DcDebug.dumpMatrixOrVector("[DcLinearSolver] J (after clamp)", J);
        }

        // 3) Lös G·V = J (Apache Commons Math)
        DecompositionSolver solver = new LUDecomposition(G).getSolver();
        RealVector V = solver.solve(J);

        // 4) Returnera som double[]
        double[] out = new double[net.n];
        for (int i = 0; i < net.n; i++) out[i] = V.getEntry(i);

        if (v) {
            DcDebug.dumpVector("[DcLinearSolver] V (node voltages)", out);
            DcDebug.log("[DcLinearSolver] Solve complete.");
            DcDebug.line();
        }

        return out;
    }

    /** Sätter rad/kolumn = 0 för gnd, diag=1 och J[gnd]=0. */
    public static void clampGround(RealMatrix G, RealVector J, int gnd) {
        final int n = G.getRowDimension();
        for (int r = 0; r < n; r++) G.setEntry(r, gnd, 0.0);
        for (int c = 0; c < n; c++) G.setEntry(gnd, c, 0.0);
        G.setEntry(gnd, gnd, 1.0);
        J.setEntry(gnd, 0.0);
    }

    // ------------------------------------------------------------------------
    // Fallback-stämpning (robust via reflection så vi slipper bero på exakta API-namn)
    // ------------------------------------------------------------------------

    private static int applySubstationFallbackStamp(RealMatrix G, RealVector J, DcNet net, boolean v) {
        Collection<?> subs = tryCollect(net, "substations");
        if (subs == null || subs.isEmpty()) return 0;

        int n = G.getRowDimension();
        int stamped = 0;

        for (Object ss : subs) {
            // Stöd explicit för SubstationData{id=SS, a=1, b=0, emf_V=900.0, rint_ohm=2.0, ...}
            Integer a = tryInt(ss, "a", "getA");
            Integer b = tryInt(ss, "b", "getB");
            if ((a != null && (a < 0 || a >= n)) || (b != null && (b < 0 || b >= n))) {
                if (v) DcDebug.log("[DcLinearSolver] Fallback: substation node index out of range: a=%s b=%s", a, b);
                continue;
            }

            // Om a/b saknas, försök tidigare generiska vägen (node / nodeId)
            if (a == null && b == null) {
                Integer k = resolveNodeIndexForSubstation(net, ss);
                if (k == null || k < 0 || k >= n) {
                    if (v) {
                        DcDebug.log("[DcLinearSolver] Fallback: skip substation (no node index) on %s", ss.getClass().getName());
                        debugSubstationShape(ss);
                    }
                    continue;
                }
                // Om bara ett index hittades, antag b = ground
                a = k;
                b = net.groundIndex();
            }

            // Hämta E och R – stöd både för generiska namn och SubstationData-specifika
            Double E = resolveDouble(ss, new String[]{
                    "getEmf_V", "emf_V", // SubstationData
                    "emf","getEmf","voltage","getVoltage","u","getU",
                    "nominalVoltage","getNominalVoltage","targetVoltage","getTargetVoltage",
                    "E","getE","V","getV"
            });
            Double R = resolveDouble(ss, new String[]{
                    "getRint_ohm", "rint_ohm", // SubstationData
                    "rInternal","getRInternal","resistance","getResistance",
                    "sourceResistance","getSourceResistance",
                    "internalResistance","getInternalResistance",
                    "ri","getRi","R","getR"
            });

            if (E == null || R == null || !Double.isFinite(R) || R <= 0.0) {
                if (v) {
                    DcDebug.log("[DcLinearSolver] Fallback: skip substation (E/R missing) on %s", ss.getClass().getName());
                    debugSubstationShape(ss);
                }
                continue;
            }

            // Norton mellan noder a och b: g = 1/R, I = E/R med injektion +I på a och -I på b
            double g = 1.0 / R;
            double I = E * g;
            int ai = a;
            int bi = (b != null ? b : net.groundIndex());

            // Stamp G
            G.addToEntry(ai, ai, g);
            G.addToEntry(bi, bi, g);
            G.addToEntry(ai, bi, -g);
            G.addToEntry(bi, ai, -g);

            // Stamp J (vektor)
            J.addToEntry(ai, +I);
            J.addToEntry(bi, -I);

            stamped++;
            if (v) DcDebug.log("[DcLinearSolver] Fallback stamp: a=%d, b=%d, E=%.3f V, R=%.6f Ω (g=%.6f S, I=%.3f A)",
                    ai, bi, E, R, g, I);
        }
        return stamped;
    }

    private static Integer resolveNodeIndexForSubstation(DcNet net, Object ss) {
        // Direkta int-getters
        Integer k = tryInt(ss, "node", "nodeIndex", "getNode", "getNodeIndex", "idx", "getIdx", "bus", "getBus", "at", "getAt");
        if (k != null) return k;

        // Identifierare → index
        Object nodeId = tryAny(ss, "nodeId", "getNodeId", "node", "getNode", "id", "getId", "busId", "getBusId", "at", "getAt");
        if (nodeId == null) {
            nodeId = tryFieldAny(ss, "nodeId", "node", "id", "busId", "at");
        }
        if (nodeId == null) return null;

        // idxOf(id) med exakt typ
        try {
            Method idxOf = net.getClass().getMethod("idxOf", nodeId.getClass());
            Object res = idxOf.invoke(net, nodeId);
            if (res instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}

        // idxOf(Object)
        try {
            Method idxOf = net.getClass().getMethod("idxOf", Object.class);
            Object res = idxOf.invoke(net, nodeId);
            if (res instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}

        // indexMapFromIds().get(...)
        try {
            Method m = net.getClass().getMethod("indexMapFromIds");
            Object map = m.invoke(net);
            if (map instanceof Map<?, ?> mp) {
                Object idx = mp.get(nodeId);
                if (idx instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {}

        // nodeIds() -> lista
        try {
            Method m = net.getClass().getMethod("nodeIds");
            Object lst = m.invoke(net);
            if (lst instanceof java.util.List<?> L) {
                for (int i = 0; i < L.size(); i++) {
                    Object x = L.get(i);
                    if (nodeId == null ? x == null : nodeId.equals(x)) return i;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // ------------------------------------------------------------------------
    // Små helpers
    // ------------------------------------------------------------------------

    private static boolean isZeroVector(RealVector v) {
        for (int i = 0; i < v.getDimension(); i++) {
            if (v.getEntry(i) != 0.0) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Collection<?> tryCollect(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object res = m.invoke(target);
            if (res instanceof Collection<?> c) return c;
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object tryAny(Object target, String... methods) {
        for (String name : methods) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object tryFieldAny(Object target, String... fields) {
        for (String f : fields) {
            try {
                Field fld = target.getClass().getField(f);
                fld.setAccessible(true);
                return fld.get(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Integer tryInt(Object target, String... names) {
        // getters
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                Object v = m.invoke(target);
                if (v instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }
        // public fields
        for (String name : names) {
            try {
                Field f = target.getClass().getField(name);
                Object v = f.get(target);
                if (v instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Double resolveDouble(Object target, String[] candidates) {
        // getters
        Double d = tryDouble(target, candidates);
        if (d != null) return d;
        // fields
        d = tryFieldDouble(target, candidates);
        return d;
    }

    private static Double tryDouble(Object target, String... methods) {
        for (String name : methods) {
            try {
                Method m = target.getClass().getMethod(name);
                Object v = m.invoke(target);
                if (v instanceof Number n) return n.doubleValue();
                // stöd för org.dcsim.math.Real.asDouble()
                try {
                    Method asDouble = v.getClass().getMethod("asDouble");
                    Object dv = asDouble.invoke(v);
                    if (dv instanceof Number dn) return dn.doubleValue();
                } catch (Throwable ignoreReal) {}
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Double tryFieldDouble(Object target, String... fields) {
        for (String f : fields) {
            try {
                Field fld = target.getClass().getField(f);
                Object v = fld.get(target);
                if (v instanceof Number n) return n.doubleValue();
                try {
                    Method asDouble = v.getClass().getMethod("asDouble");
                    Object dv = asDouble.invoke(v);
                    if (dv instanceof Number dn) return dn.doubleValue();
                } catch (Throwable ignoreReal) {}
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void debugSubstationShape(Object ss) {
        try {
            Class<?> c = ss.getClass();
            DcDebug.log("[SubstationData] type=%s toString=%s", c.getName(), String.valueOf(ss));
            // lista publika getters
            StringBuilder gm = new StringBuilder();
            for (Method m : c.getMethods()) {
                if (m.getParameterCount() == 0) {
                    String n = m.getName();
                    if (n.startsWith("get") || n.startsWith("is") || n.equals("node") || n.equals("nodeId") || n.equals("id") || n.equals("at") || n.equals("bus") || n.equals("idx") || n.equals("a") || n.equals("b")) {
                        gm.append(n).append("(), ");
                    }
                }
            }
            DcDebug.log("[SubstationData] getters: %s", gm.toString());
            // lista publika fält
            StringBuilder gf = new StringBuilder();
            for (Field f : c.getFields()) {
                gf.append(f.getName()).append(", ");
            }
            DcDebug.log("[SubstationData] fields: %s", gf.toString());
        } catch (Throwable t) {
            DcDebug.log("[SubstationData] debug error: %s", String.valueOf(t));
        }
    }
}
