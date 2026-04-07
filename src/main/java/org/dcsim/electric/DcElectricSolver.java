package org.dcsim.electric;

import org.apache.commons.math3.linear.*;
import org.dcsim.math.Real;

import java.util.*;
import java.lang.reflect.Method;

/**
 * Minimal, compiling fallback DC solver.
 * - Builds node index from devices.
 * - Stamps Lines/TrainLoads via their stamp(...) methods.
 * - Stamps Substations (duck-typed) as Norton sources (G=1/R, J=G*E).
 * - Solves Y·V = J and writes back voltages and per-device currents/powers.
 *
 * This is a temporary solver to be replaced by DcIterativeSolver.
 */
public class DcElectricSolver implements ElectricSolver {

    @Override
    public GridResult solve(GridModel model, double timeSec, int timestep) {

        // -------------------------------
        // 1) Build node index
        // -------------------------------
        final Map<String, Integer> nodeIndex = new LinkedHashMap<>();

        // Known two-node devices first
        for (Object o : model.getDevices()) {
            if (o instanceof Line line) {
                ensureNode(nodeIndex, line.getFromNode());
                ensureNode(nodeIndex, line.getToNode());
            } else if (o instanceof TrainLoad tl) {
                ensureNode(nodeIndex, tl.getFromNode());
                ensureNode(nodeIndex, tl.getToNode());
            }
        }
// Substations (duck-typed)
        for (Object o : model.getDevices()) {
            if (looksLikeSubstation(o)) {
                String n = readNodeId(o);
                if (n != null) {
                    ensureNode(nodeIndex, n);
                } else {
                    String a = readString(o, "getFromNode");
                    String b = readString(o, "getToNode");
                    if (a != null) ensureNode(nodeIndex, a);
                    if (b != null) ensureNode(nodeIndex, b);
                }
            }
        }

        final int N = nodeIndex.size();
        RealMatrix Y = new Array2DRowRealMatrix(N, N);
        RealVector J = new ArrayRealVector(N);
        RealVector X = new ArrayRealVector(N); // provided to stampers if they need it

        // -------------------------------
        // 2) Stamp devices
        // -------------------------------
        // Lines + TrainLoads use their own stamp(...)
        for (Object o : model.getDevices()) {
            if (o instanceof Line line) {
                line.stamp(Y, J, X, timestep, nodeIndex);
            } else if (o instanceof TrainLoad tl) {
                tl.stamp(Y, J, X, timestep, nodeIndex);
            }
        }

        // Substations (Norton): G=1/R to ground, J=G*E
        for (Object o : model.getDevices()) {
            if (looksLikeSubstation(o)) {
                String n = readNodeId(o);
                if (n == null) n = readString(o, "getFromNode"); // fallback
                Double Rint = orDoubles(o, "getInternalResistance", "getRint", "getInternalR");
                Double E    = orDoubles(o, "getEmf", "getOpenCircuitVoltage");
                if (n != null && Rint != null && E != null && !n.equals("") && Rint > 0.0) {
                    double Gsh = 1.0 / Rint;
                    int i = nodeIndex.get(n);
                    Y.addToEntry(i, i, Gsh);
                    J.addToEntry(i, Gsh * E);
                }
            }
        }

        // Liten regularisering för robusthet (kan sänkas/avaktiveras senare)
        for (int i = 0; i < N; i++) {
            Y.addToEntry(i, i, 1e-9);
        }

        // -------------------------------
        // 3) Solve Y·V = J
        // -------------------------------
        RealVector V = new LUDecomposition(Y).getSolver().solve(J);

        // -------------------------------
        // 4) Build result + write node voltages
        // -------------------------------
        GridResult result = new GridResult(Y, J);
        for (Map.Entry<String, Integer> e : nodeIndex.entrySet()) {
            String nodeId = e.getKey();
            int idx    = e.getValue();
            result.setVoltage(nodeId, Real.fromDouble(V.getEntry(idx)));
        }

        // -------------------------------
        // 5) Per-device telemetry
        // -------------------------------
        // Lines: I = computeCurrent(Va,Vb); P = ΔV * I
        for (Object o : model.getDevices()) {
            if (o instanceof Line line) {
                int i = nodeIndex.get(line.getFromNode());
                int j = nodeIndex.get(line.getToNode());
                Real fromV = Real.fromDouble(V.getEntry(i));
                Real toV   = Real.fromDouble(V.getEntry(j));
                Real I     = line.computeCurrent(fromV, toV);
                double dV  = fromV.asDouble() - toV.asDouble();
                Real P     = Real.fromDouble(dV * I.asDouble()); // physical network power
                result.setCurrent(line.getId(), I);
                result.setPower(line.getId(), P);
            }
        }

        // TrainLoads: I from computeCurrent; P = ΔV * I (not the requested/profile power)
        for (Object o : model.getDevices()) {
            if (o instanceof TrainLoad tl) {
                int i = nodeIndex.get(tl.getFromNode());
                int j = nodeIndex.get(tl.getToNode());
                Real fromV = Real.fromDouble(V.getEntry(i));
                Real toV   = Real.fromDouble(V.getEntry(j));
                Real I     = tl.computeCurrent(fromV, toV);
                double dV  = fromV.asDouble() - toV.asDouble();
                Real P     = Real.fromDouble(dV * I.asDouble()); // physical network power
                result.setCurrent(tl.getId(), I);
                result.setPower(tl.getId(), P);
                // keep requested power (if API provides it) for diagnostics
                try {
                    result.setRequestedPower(tl.getId(), tl.getRequestedPower());
                } catch (Throwable ignore) { /* optional */ }
            }
        }

        // Substations: I = (E - Vn)/Rint ; P = Vn * I
        for (Object o : model.getDevices()) {
            if (looksLikeSubstation(o)) {
                String  n = readNodeId(o);
                if (n == null) n = readString(o, "getFromNode");
                Double Rint = orDoubles(o, "getInternalResistance", "getRint", "getInternalR");
                Double E    = orDoubles(o, "getEmf", "getOpenCircuitVoltage");
                if (n != null && Rint != null && E != null && !n.equals("") && Rint > 0.0) {
                    int idx = nodeIndex.get(n);
                    double Vn = V.getEntry(idx);
                    double I  = (E - Vn) / Rint;
                    double P  = Vn * I;
                    String id = readId(o);
                    if (id != null) {
                        result.setCurrent(id, Real.fromDouble(I));
                        result.setPower(id,   Real.fromDouble(P));
                    }
                }
            }
        }

        return result;
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------
    private static void ensureNode(Map<String,Integer> index, String nodeId) {
        if (nodeId.equals("")) return;
        if (!index.containsKey(nodeId)) {
            index.put(nodeId, index.size());
        }
    }

    /** Back-compat adapter; not used by this fallback solver. */
    public void setTrainAnchors(java.util.Collection<?> anchors, double dtSec) {
        // no-op
    }

    // ------ duck-typing for Substation ------
    private static boolean looksLikeSubstation(Object o) {
        return (hasMethod(o, "getEmf") || hasMethod(o, "getOpenCircuitVoltage"))
                && (hasMethod(o, "getInternalResistance") || hasMethod(o, "getRint") || hasMethod(o, "getInternalR"))
                && (hasMethod(o, "getNodeId") || hasMethod(o, "getFromNode"));
    }

    private static boolean hasMethod(Object o, String name) {
        try { o.getClass().getMethod(name); return true; }
        catch (NoSuchMethodException e) { return false; }
    }

    private static String readNodeId(Object o) {
        return readString(o, "getNodeId");
    }

    private static String readString(Object o, String method) {
        try {
            Object v = o.getClass().getMethod(method).invoke(o);
            return v != null ? v.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Double readDouble(Object o, String method) {
        try {
            Object v = o.getClass().getMethod(method).invoke(o);
            if (v instanceof Real)   return ((Real) v).asDouble();
            if (v instanceof Number) return ((Number) v).doubleValue();
            return null;
        } catch (Throwable t) { return null; }
    }

    private static Double orDoubles(Object o, String... mnames) {
        for (String m : mnames) {
            Double v = readDouble(o, m);
            if (v != null) return v;
        }
        return null;
    }

    private static String readId(Object o) {
        try { Object v = o.getClass().getMethod("getId").invoke(o); return (String) v; }
        catch (Throwable t) { return null; }
    }
}
