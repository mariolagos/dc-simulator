// org/dcsim/electric/Line.java
package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.Map;

public class Line implements Device<Real> {

    private final int fromNode;
    private final int toNode;
    private final Real resistance;
    private final String description;
    private final String category;

    // --- Enda "riktiga" konstruktorn ---
    public Line(int fromNode, int toNode, Real resistance, String description, String category) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.resistance = resistance;
        this.description = (description == null || description.isBlank()) ? "line" : description;
        this.category = (category == null || category.isBlank()) ? "catenary1" : category;
    }

    // --- Fabriksmetoder för vanliga fall ---
    public static Line of(int fromNode, int toNode, Real resistance) {
        return new Line(fromNode, toNode, resistance, "line", "catenary1");
    }

    public static Line of(int fromNode, int toNode, Real resistance, String description) {
        return new Line(fromNode, toNode, resistance, description, "catenary1");
    }

    public static Line ofCategory(int fromNode, int toNode, Real resistance, String category) {
        return new Line(fromNode, toNode, resistance, "line", category);
    }

    // --- Device<Real> impl + getters (oförändrade i övrigt) ---
    @Override public String getId() { return "L_" + fromNode + "_" + toNode; }
    @Override public int getConnectedNode() { throw new UnsupportedOperationException("Line is two-node device"); }
    @Override public Real getCurrent() { return Real.ZERO; }
    @Override public Real getPower() { return Real.ZERO; }

    @Override
    public Real computeCurrent(Real voltage, double time) {
        throw new UnsupportedOperationException("Use computeCurrent(fromV, toV) for two-node device");
    }

    public Real computeCurrent(Real fromV, Real toV) {
        Real dv = fromV.minus(toV);
        return dv.divide(resistance);
    }

    public Real getPower(Real fromV, Real toV) {
        Real i = computeCurrent(fromV, toV);
        return fromV.minus(toV).times(i);
    }

    @Override
    public void stamp(RealMatrix y, RealVector j, RealVector x, int step, Map<Integer,Integer> nodeIndex) {
        int i = nodeIndex.get(fromNode);
        int k = nodeIndex.get(toNode);

        double g = 1.0 / resistance.asDouble();
        y.addToEntry(i, i, g);
        y.addToEntry(k, k, g);
        y.addToEntry(i, k, -g);
        y.addToEntry(k, i, -g);
    }

    public int getFromNode() { return fromNode; }
    public int getToNode() { return toNode; }
    public Real getResistance() { return resistance; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }

    @Override
    public String toString() {
        return String.format("Line(%d->%d, R=%.6f, desc=%s, cat=%s)",
                fromNode, toNode, resistance.asDouble(), description, category);
    }
}
