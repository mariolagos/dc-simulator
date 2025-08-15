package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.FieldElement;
import org.dcsim.math.Real;

import java.util.Map;

public class Line implements Device<Real> {
    private String description; // Optional descriptive text

    private final int fromNode;
    private final int toNode;
    private final Real resistance;
    private Real current = Real.ZERO;

    public Line(int fromNode, int toNode, Real resistance) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.resistance = resistance;
    }
    public Line(int fromNode, int toNode, Real resistance, String description) {
        this(fromNode, toNode, resistance);
        this.description = description;
    }


    @Override
    public String getId() {
        return "L_" + fromNode + "_" + toNode;
    }

    @Override
    public int getConnectedNode() {
        throw new UnsupportedOperationException("Line is connected to two nodes");
    }

    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector, int timestep, Map<Integer, Integer> nodeIndexMap) {
        int i = nodeIndexMap.get(fromNode);
        int j = nodeIndexMap.get(toNode);
        double g = 1.0 / resistance.asDouble();

        yMatrix.addToEntry(i, i, g);
        yMatrix.addToEntry(j, j, g);
        yMatrix.addToEntry(i, j, -g);
        yMatrix.addToEntry(j, i, -g);
    }

    public void computeCurrent(Real fromVoltage, Real toVoltage) {
        this.current = fromVoltage.minus(toVoltage).divide(resistance);
    }

    public void computeCurrent(double fromVoltage, double toVoltage) {
        this.current = Real.fromDouble(fromVoltage - toVoltage).divide(resistance);
    }

    @Override
    public Real getCurrent() {
        return current;
    }

    public double getCurrentAsDouble() {
        return current.asDouble();
    }

    public Real getPower(Real fromVoltage, Real toVoltage) {
        return current.times(fromVoltage.minus(toVoltage));
    }

    public double getPower(double fromVoltage, double toVoltage) {
        return getCurrentAsDouble() * (fromVoltage - toVoltage);
    }

    public int getFromNode() {
        return fromNode;
    }

    public int getToNode() {
        return toNode;
    }

    public Real getResistance() {
        return resistance;
    }

    @Override
    public Real getPower() {
        throw new UnsupportedOperationException("Use getPower(from, to) instead.");
    }

    @Override
    public Real computeCurrent(Real nodeVoltage, double time) {
        throw new UnsupportedOperationException("Use computeCurrent(from, to) instead.");
    }

    public String getDescription() {
        return description;
    }
}
