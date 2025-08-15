package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.Map;

public class Substation implements Device<Real> {
    private String description; // Optional descriptive text

    private final String id;
    private final int fromNode; // typiskt: 0 (jord)
    private final int toNode;   // typiskt: matarspänning
    private final Real emf;     // ideal EMF [V]
    private final Real internalResistance; // [Ω]
    private Real current = Real.ZERO;
    private Real power = Real.ZERO;

    public Substation(String id, int fromNode, int toNode, Real emf, Real internalResistance) {
        this.id = id;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.emf = emf;
        this.internalResistance = internalResistance;
    }
    public Substation(String id, int fromNode, int toNode, Real emf, Real internalResistance, String description) {
        this(id, fromNode, toNode, emf, internalResistance);
        this.description = description;
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

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getConnectedNode() {
        throw new UnsupportedOperationException("Substation is a two-node device");
    }

    @Override
    public Real getCurrent() {
        return current;
    }

    @Override
    public Real getPower() {
        return power;
    }

    @Override
    public Real computeCurrent(Real nodeVoltage, double time) {
        throw new UnsupportedOperationException("Use computeCurrent(from, to) instead");
    }

    public Real computeCurrent(Real fromVoltage, Real toVoltage) {
        // I = (emf - (V_to - V_from)) / R
        Real voltageDiff = emf.minus(toVoltage.minus(fromVoltage));
        current = voltageDiff.divide(internalResistance);
        power = emf.times(current);
        return current;
    }

    public Real computePower(Real fromVoltage, Real toVoltage) {
        computeCurrent(fromVoltage, toVoltage);
        return power;
    }

    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector,
                      int timestep, Map<Integer, Integer> nodeIndexMap) {

        int i = nodeIndexMap.get(fromNode);
        int j = nodeIndexMap.get(toNode);

        double u_i = xVector != null ? xVector.getEntry(i) : emf.asDouble();
        double u_j = xVector != null ? xVector.getEntry(j) : 0.0;
        double voltageDiff = u_i - u_j;

        if (voltageDiff >= emf.asDouble()) {
            // Backfeed blockeras av likriktare
            return;
        }

        double g = 1.0 / internalResistance.asDouble();
        double jval = emf.asDouble() * g;

        yMatrix.addToEntry(i, i, g);
        yMatrix.addToEntry(j, j, g);
        yMatrix.addToEntry(i, j, -g);
        yMatrix.addToEntry(j, i, -g);

        jVector.addToEntry(i, jval);
        jVector.addToEntry(j, -jval);
    }

    public String getDescription() {
        return description;
    }
}
