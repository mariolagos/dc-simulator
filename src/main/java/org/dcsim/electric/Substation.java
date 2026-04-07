package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.Map;

/**
 * DC substation modeled with a Norton equivalent wrt. ground:
 * - Current source I = E/Rint in parallel with conductance G = 1/Rint.
 *
 * Diode/backfeed behavior:
 * - If allowBackfeed == false (diode toward the DC bus), the substation only
 *   delivers power when v = V(from) - V(to) < E. Otherwise it is open.
 * - If allowBackfeed == true, the Norton is always active (can absorb power).
 *
 * Optional current limit:
 * - If maxCurrentA is set (>0), the net Norton current is clamped to ±maxCurrentA
 *   after applying diode/backfeed logic.
 *
 * Power accounting (when active):
 *   i_net = G * (E - v)     (positive = delivering to the network)
 *   P_out = v * i_net       (power delivered to the network)
 *   P_int = (E - v) * i_net (internal loss)
 */
public class Substation implements Device<Real> {

    static {
        System.out.println("[WHERE] Substation loaded from: " +
                Substation.class.getProtectionDomain().getCodeSource().getLocation());
    }

    private static final double EPS = 1e-6;
    private static final double G_LEAK = 1e-9;

    private String description;
    private final String id;
    private final String fromNode;
    private final String toNode;
    private final String groundNodeId;
    private Real emf;
    private Real internalResistance;

    private Real current = Real.ZERO;
    private Real power = Real.ZERO;
    private Real internalLoss = Real.ZERO;

    private boolean allowBackfeed = false;
    private double maxCurrentA = -1.0;

    public Substation(
            String id,
            String fromNode,
            String toNode,
            String groundNodeId,
            Real emf,
            Real internalResistance
    ) {
        this.id = id;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.groundNodeId = groundNodeId;
        this.emf = emf;
        this.internalResistance = internalResistance;
    }

    public Substation(
            String id,
            String fromNode,
            String toNode,
            String groundNodeId,
            Real emf,
            Real internalResistance,
            String description
    ) {
        this(id, fromNode, toNode, groundNodeId, emf, internalResistance);
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getFromNode() {
        return fromNode;
    }

    public String getToNode() {
        return toNode;
    }

    public String getGroundNodeId() {
        return groundNodeId;
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

    @Override
    public String getConnectedNode() {
        throw new UnsupportedOperationException("Substation is a two-node device");
    }

    @Override
    public Real computeCurrent(Real voltage, double time) {
        throw new UnsupportedOperationException("Use computeCurrent(fromV, toV)");
    }

    public Real computeCurrent(Real fromVoltage, Real toVoltage) {
        final double E = emf.asDouble();
        final double R = internalResistance.asDouble();
        final double G = (R > 0.0) ? 1.0 / R : 1e9;

        final double v = fromVoltage.minus(toVoltage).asDouble();
        double iNet = G * (E - v);

        if (!allowBackfeed) {
            iNet = Math.max(0.0, iNet);
        }

        if (hasMaxCurrent()) {
            iNet = Math.copySign(Math.min(Math.abs(iNet), getMaxCurrentA()), iNet);
        }

        this.current = Real.fromDouble(iNet);
        this.power = Real.fromDouble(v * iNet);
        this.internalLoss = Real.fromDouble((E - v) * iNet);
        return this.current;
    }

    public Real computePower(Real fromVoltage, Real toVoltage) {
        final double v = fromVoltage.minus(toVoltage).asDouble();
        return Real.fromDouble(v * this.current.asDouble());
    }

    public Real computeInternalLoss(Real fromVoltage, Real toVoltage) {
        final double E = emf.asDouble();
        final double R = internalResistance.asDouble();
        final double G = (R > 0.0) ? 1.0 / R : 1e9;
        final double v = fromVoltage.minus(toVoltage).asDouble();

        double iNet = G * (E - v);
        if (!allowBackfeed) {
            iNet = Math.max(0.0, iNet);
        }
        if (hasMaxCurrent()) {
            iNet = Math.copySign(Math.min(Math.abs(iNet), getMaxCurrentA()), iNet);
        }

        return Real.fromDouble((E - v) * iNet);
    }

    @Override
    public void stamp(
            RealMatrix yMatrix,
            RealVector jVector,
            RealVector xVector,
            int timestep,
            Map<String, Integer> nodeIndexMap
    ) {
        Integer iObj = nodeIndexMap.get(fromNode);
        Integer jObj = nodeIndexMap.get(toNode);
        if (iObj == null || jObj == null) {
            return;
        }

        final int i = iObj;
        final int j = jObj;

        final double R = internalResistance.asDouble();
        final double G = (R > 0.0) ? 1.0 / R : 1e9;
        final double E = emf.asDouble();

        double Vi = 0.0;
        double Vj = 0.0;
        if (xVector != null) {
            try {
                Vi = xVector.getEntry(i);
                Vj = xVector.getEntry(j);
            } catch (Exception ignore) {
            }
        }

        final double dV = Vi - Vj;
        final boolean forward = (dV + EPS < E);
        final boolean fullStamp = allowBackfeed || forward;

        if (fullStamp) {
            if (i == j) {
                yMatrix.addToEntry(i, i, G);
            } else {
                yMatrix.addToEntry(i, i, G);
                yMatrix.addToEntry(j, j, G);
                yMatrix.addToEntry(i, j, -G);
                yMatrix.addToEntry(j, i, -G);
            }

            double rawI = G * (E - dV);
            if (!allowBackfeed) {
                rawI = Math.max(0.0, rawI);
            }
            if (hasMaxCurrent()) {
                rawI = Math.copySign(Math.min(Math.abs(rawI), getMaxCurrentA()), rawI);
            }

            double I_src = rawI + G * dV;

            jVector.addToEntry(i, +I_src);
            jVector.addToEntry(j, -I_src);
        } else {
            if (i == j) {
                yMatrix.addToEntry(i, i, G_LEAK);
            } else {
                yMatrix.addToEntry(i, i, G_LEAK);
                yMatrix.addToEntry(j, j, G_LEAK);
                yMatrix.addToEntry(i, j, -G_LEAK);
                yMatrix.addToEntry(j, i, -G_LEAK);
            }
        }
    }

    public void setInternalResistance(Real real) {
        internalResistance = real;
    }

    public void setEmf(Real real) {
        emf = real;
    }
}