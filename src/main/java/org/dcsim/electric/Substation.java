package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.Map;

/**
 * DC substation modeled with a Norton equivalent to ground:
 * - Current source I = E/Rint in parallel with conductance G = 1/Rint to ground (when toNode == ground)
 *
 * Power accounting:
 *  i_net = G * (E - v), where v = V(from) - V(to)
 *  P_out (to network) = v * i_net
 *  P_int (internal loss) = (E - v) * i_net    // series-equivalent internal loss
 *
 * Note: For stability and clean ground clamping, we stamp a ground-aware Norton when toNode == groundNodeId.
 */
public class Substation implements Device<Real> {

    private String description; // Optional
    private final String id;
    private final int fromNode;           // typically the fed node
    private final int toNode;             // typically the return (ground)
    private final int groundNodeId;       // explicit ground id from model
    private final Real emf;               // [V]
    private final Real internalResistance;// [ohm]

    private Real current = Real.ZERO;     // last computed i_net (to network)
    private Real power   = Real.ZERO;     // last computed P_out (to network)
    private Real internalLoss = Real.ZERO;// last computed P_int

    public Substation(String id, int fromNode, int toNode, int groundNodeId, Real emf, Real internalResistance) {
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

    public String getId() { return id; }
    public int getFromNode() { return fromNode; }
    public int getToNode() { return toNode; }
    public Real getEmf() { return emf; }
    public Real getInternalResistance() { return internalResistance; }
    public Real getCurrent() { return current; }
//    public Real getPower() { return power; }
    public Real getInternalLoss() { return internalLoss; }

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

    public Real computeCurrent(Real fromVoltage, Real toVoltage) {
        double v = fromVoltage.minus(toVoltage).asDouble();
        double R = internalResistance.asDouble();
        double G = (R > 0.0) ? 1.0 / R : 1e9;
        double E = emf.asDouble();

        double iNet = G * (E - v); // net current delivered to the network
        this.current = Real.fromDouble(iNet);

        // Keep power members consistent (optional precompute here)
        this.power = Real.fromDouble(v * iNet);
        this.internalLoss = Real.fromDouble((E - v) * iNet);

        return this.current;
    }

    public Real computePower(Real fromVoltage, Real toVoltage) {
        // P_out (to network)
        double v = fromVoltage.minus(toVoltage).asDouble();
        if (this.current == null) computeCurrent(fromVoltage, toVoltage);
        return Real.fromDouble(v * this.current.asDouble());
    }

    /** Internal loss in substation's internal resistance (series-equivalent). */
    public Real computeInternalLoss(Real fromVoltage, Real toVoltage) {
        double v = fromVoltage.minus(toVoltage).asDouble();
        double R = internalResistance.asDouble();
        double G = (R > 0.0) ? 1.0 / R : 1e9;
        double E = emf.asDouble();
        double iNet = G * (E - v);
        return Real.fromDouble((E - v) * iNet);
    }

    @Override
    public Real getPower() {
        // Not meaningful without node voltages
        return power;
    }

    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector,
                      int timestep, Map<Integer, Integer> nodeIndexMap) {

        int i = nodeIndexMap.get(fromNode);
        int j = nodeIndexMap.get(toNode);

        double R = internalResistance.asDouble();
        double G = (R > 0.0) ? 1.0 / R : 1e9;
        double I = emf.asDouble() * G;

        boolean toIsGround = (toNode == groundNodeId);

        if (toIsGround) {
            // Ground-aware Norton (Vg=0 enforced by solver)
            yMatrix.addToEntry(i, i, G);
            jVector.addToEntry(i, I);
        } else {
            // General two-node Norton
            yMatrix.addToEntry(i, i, G);
            yMatrix.addToEntry(j, j, G);
            yMatrix.addToEntry(i, j, -G);
            yMatrix.addToEntry(j, i, -G);
            jVector.addToEntry(i, I);
            jVector.addToEntry(j, -I);
        }
    }
}
