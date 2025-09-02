package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.Map;

/**
 * DC substation modeled with a Norton equivalent wrt. ground:
 * - Current source I = E/Rint in parallel with conductance G = 1/Rint.
 * <p>
 * Diode/backfeed behavior:
 * - If allowBackfeed == false (diode toward the DC bus), the substation only
 * delivers power when v = V(from) - V(to) < E. Otherwise it is open (no stamp or a tiny leak).
 * - If allowBackfeed == true, the Norton is always active (can absorb power).
 * <p>
 * Optional current limit:
 * - If maxCurrentA is set (>0), the net Norton current is clamped to ±maxCurrentA
 * after applying diode/backfeed logic.
 * <p>
 * Power accounting (when active):
 * i_net = G * (E - v)          (positive = delivering to the network from 'to'→'from')
 * P_out = v * i_net            (power delivered to the network)
 * P_int = (E - v) * i_net      (internal loss; equals G*(E - v)^2 when not current-limited)
 */
public class Substation implements Device<Real> {

    static {
        System.out.println("[WHERE] Substation loaded from: " +
                Substation.class.getProtectionDomain().getCodeSource().getLocation());
    }

    private static final double EPS = 1e-6;  // small tolerance for comparisons
    private static final double G_LEAK = 1e-9;  // tiny leak conductance for numerical stability

    private String description;              // Optional
    private final String id;
    private final int fromNode;              // DC bus node (positive terminal)
    private final int toNode;                // return (often ground)
    private final int groundNodeId;          // explicit ground id from the model
    private final Real emf;                  // [V]
    private final Real internalResistance;   // [ohm]

    private Real current = Real.ZERO;   // last computed i_net (to network)
    private Real power = Real.ZERO;   // last computed P_out (to network)
    private Real internalLoss = Real.ZERO;   // last computed P_int

    private boolean allowBackfeed = false;

    // Optional per-station current limit (A). <=0 means "no limit".
    private double maxCurrentA = -1.0;

    public boolean isAllowBackfeed() {
        return allowBackfeed;
    }

    public void setAllowBackfeed(boolean allow) {
        this.allowBackfeed = allow;
    }

    public boolean hasMaxCurrent() {
        return maxCurrentA > 0.0;
    }

    public double getMaxCurrentA() {
        return maxCurrentA;
    }

    public void setMaxCurrentA(double amps) {
        this.maxCurrentA = amps;
    }

    public Substation(String id, int fromNode, int toNode, int groundNodeId,
                      Real emf, Real internalResistance) {
        this.id = id;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.groundNodeId = groundNodeId;
        this.emf = emf;
        this.internalResistance = internalResistance;
    }

    public Substation(String id, int fromNode, int toNode, int groundNodeId,
                      Real emf, Real internalResistance, String description) {
        this(id, fromNode, toNode, groundNodeId, emf, internalResistance);
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public int getFromNode() {
        return fromNode;
    }

    public int getToNode() {
        return toNode;
    }

    public Real getEmf() {
        return emf;
    }

    public Real getInternalResistance() {
        return internalResistance;
    }

    public Real getCurrent() {
        return current;
    }

    public Real getPower() {
        return power;
    }

    public Real getInternalLoss() {
        return internalLoss;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public int getConnectedNode() {
        // Two-node device; not used
        throw new UnsupportedOperationException("Substation is a two-node device");
    }

    @Override
    public Real computeCurrent(Real voltage, double time) {
        // Not used; two-node form below
        throw new UnsupportedOperationException("Use computeCurrent(fromV, toV)");
    }

    /**
     * Compute i_net with diode/backfeed + optional current limit.
     */
    public Real computeCurrent(Real fromVoltage, Real toVoltage) {
        final double E = emf.asDouble();
        final double R = internalResistance.asDouble();
        final double G = (R > 0.0) ? 1.0 / R : 1e9;

        final double v = fromVoltage.minus(toVoltage).asDouble(); // V(from) - V(to)
        double iNet = G * (E - v);                                // b→a net Norton current

        if (!allowBackfeed) iNet = Math.max(0.0, iNet);           // diode: block backfeed

        if (hasMaxCurrent()) {
            iNet = Math.copySign(Math.min(Math.abs(iNet), getMaxCurrentA()), iNet);
        }

        this.current = Real.fromDouble(iNet);
        this.power = Real.fromDouble(v * iNet);            // to network
        this.internalLoss = Real.fromDouble((E - v) * iNet);      // >= 0
        return this.current;
    }

    /**
     * Power delivered to the network.
     */
    public Real computePower(Real fromVoltage, Real toVoltage) {
        if (this.current == null) computeCurrent(fromVoltage, toVoltage);
        final double v = fromVoltage.minus(toVoltage).asDouble();
        return Real.fromDouble(v * this.current.asDouble());
    }

    /**
     * Internal loss (series-equivalent Rint) with same diode/limit logic.
     */
    public Real computeInternalLoss(Real fromVoltage, Real toVoltage) {
        final double E = emf.asDouble();
        final double R = internalResistance.asDouble();
        final double G = (R > 0.0) ? 1.0 / R : 1e9;
        final double v = fromVoltage.minus(toVoltage).asDouble();

        double iNet = G * (E - v);
        if (!allowBackfeed) iNet = Math.max(0.0, iNet);
        if (hasMaxCurrent()) iNet = Math.copySign(Math.min(Math.abs(iNet), getMaxCurrentA()), iNet);

        return Real.fromDouble((E - v) * iNet);
    }

    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector,
                      int timestep, Map<Integer, Integer> nodeIndexMap) {

        Integer iObj = nodeIndexMap.get(fromNode);
        Integer jObj = nodeIndexMap.get(toNode);
        if (iObj == null || jObj == null) return;

        final int i = iObj;
        final int j = jObj;

        final double R = internalResistance.asDouble();
        final double G = (R > 0.0) ? 1.0 / R : 1e9;
        final double E = emf.asDouble();

        // Estimate present ΔV from solver iterate (if available)
        double Vi = 0.0, Vj = 0.0;
        if (xVector != null) {
            try {
                Vi = xVector.getEntry(i);
                Vj = xVector.getEntry(j);
            } catch (Exception ignore) {
                // keep zeros if not available
            }
        }
        final double dV = Vi - Vj;

        final boolean forward = (dV + EPS < E);
        final boolean fullStamp = allowBackfeed || forward;

        if (fullStamp) {
            // Conductance branch between i and j
            if (i == j) {
                yMatrix.addToEntry(i, i, G); // degenerate guard
            } else {
                yMatrix.addToEntry(i, i, G);
                yMatrix.addToEntry(j, j, G);
                yMatrix.addToEntry(i, j, -G);
                yMatrix.addToEntry(j, i, -G);
            }

            // Net Norton current (b→a) at current iterate, with diode/backfeed and limit
            double rawI = G * (E - dV);
            if (!allowBackfeed) rawI = Math.max(0.0, rawI);
            if (hasMaxCurrent()) rawI = Math.copySign(Math.min(Math.abs(rawI), getMaxCurrentA()), rawI);

            // Source that realizes the limited net current at this iterate:
            // I_src = I_lim + G * dV
            double I_src = rawI + G * dV;

            // Nodal injection convention: +I at i, -I at j
            jVector.addToEntry(i, +I_src);
            jVector.addToEntry(j, -I_src);
        } else {
            // Diode blocked: add a tiny leakage conductance for numerical stability.
            if (i == j) {
                yMatrix.addToEntry(i, i, G_LEAK);
            } else {
                yMatrix.addToEntry(i, i, G_LEAK);
                yMatrix.addToEntry(j, j, G_LEAK);
                yMatrix.addToEntry(i, j, -G_LEAK);
                yMatrix.addToEntry(j, i, -G_LEAK);
            }
            // No source injection when blocked.
        }
    }
}
