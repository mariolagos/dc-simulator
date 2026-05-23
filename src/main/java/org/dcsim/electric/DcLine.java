package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.Map;

public class DcLine implements TwoNodeDevice<Real> {
    private final String fromNode;
    private final String toNode;
    private final Real resistance;
    private Real current = Real.ZERO;
    private String description;

    public DcLine(String fromNode, String toNode, Real resistance) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.resistance = resistance;
        this.description = description;
    }

    public String getFromNode() {
        return fromNode;
    }

    public String getToNode() {
        return toNode;
    }

    public Real getResistance() {
        return resistance;
    }

    public Real computeCurrent(Real fromVoltage, Real toVoltage) {
        current = fromVoltage.minus(toVoltage).divide(resistance);
        return current;
    }

    @Override
    public String getId() {
        return "Line_" + fromNode + "_" + toNode;
    }

    @Override
    public String getConnectedNode() {
        return fromNode; // fallback
    }

    @Override
    public Real computeCurrent(Real nodeVoltage, double time) {
        throw new UnsupportedOperationException("Use computeCurrent(from, to)");
    }

    @Override
    public Real getCurrent() {
        return current;
    }

    @Override
    public Real getPower() {
        return current.times(current).times(resistance);
    }

    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector, int timestep, Map<String, Integer> nodeIndexMap) {
        int i = nodeIndexMap.get(fromNode);
        int j = nodeIndexMap.get(toNode);
        double g = 1.0 / resistance.asDouble();

        yMatrix.addToEntry(i, i, g);
        yMatrix.addToEntry(j, j, g);
        yMatrix.addToEntry(i, j, -g);
        yMatrix.addToEntry(j, i, -g);
    }

    public String getDescription() {
        return description;
    }
}
