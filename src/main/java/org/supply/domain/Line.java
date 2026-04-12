package org.supply.domain;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.electric.Device;
import org.dcsim.math.Real;

import java.util.Map;
import java.util.Objects;

public class Line implements Device<Real> {

    // --- Internal / solver-facing ---
    private final String fromNode;
    private final String toNode;
    private final Real resistance;
    private final String description;
    private final String category;
    private final double length;

    // --- Public / contract-facing ---
    private final String line_id;
    private final String node_from_id;
    private final String node_to_id;
    private final String track_id;

    // --- New main constructor ---
    public Line(
            String fromNode,
            String toNode,
            Real resistance,
            String description,
            String category,
            double length,
            String line_id,
            String node_from_id,
            String node_to_id,
            String track_id
    ) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.resistance = Objects.requireNonNull(resistance);
        this.description = (description == null || description.isBlank()) ? "line" : description;
        this.category = (category == null || category.isBlank()) ? "catenary1" : category;
        this.length = length;

        this.line_id = (line_id == null || line_id.isBlank())
                ? "L_" + fromNode + "_" + toNode
                : line_id;
        this.node_from_id = node_from_id;
        this.node_to_id = node_to_id;
        this.track_id = track_id;
    }

    // --- Legacy constructor kept for backward compatibility ---
    public Line(String fromNode, String toNode, Real resistance, String description, String category, double length) {
        this(
                fromNode,
                toNode,
                resistance,
                description,
                category,
                length,
                null,
                null,
                null,
                null
        );
    }

    // --- Factory methods for legacy usage ---
    public static Line of(String fromNode, String toNode, Real resistance, double length) {
        return new Line(fromNode, toNode, resistance, "line", "catenary1", length);
    }

    public static Line of(String fromNode, String toNode, Real resistance, String description, double length) {
        return new Line(fromNode, toNode, resistance, description, "catenary1", length);
    }

    public static Line ofCategory(String  fromNode, String toNode, Real resistance, String category, double length) {
        return new Line(fromNode, toNode, resistance, "line", category, length);
    }

    // --- Factory method for new contract-driven code ---
    public static Line ofContract(
            String fromNode,
            String toNode,
            Real resistance,
            double length,
            String line_id,
            String node_from_id,
            String node_to_id,
            String track_id
    ) {
        return new Line(
                fromNode,
                toNode,
                resistance,
                "line",
                "catenary1",
                length,
                line_id,
                node_from_id,
                node_to_id,
                track_id
        );
    }

    // --- Device<Real> impl + getters (preserved) ---
    @Override
    public String getId() {
        return line_id;
    }

    @Override
    public String getConnectedNode() {
        throw new UnsupportedOperationException("Line is two-node device");
    }

    @Override
    public Real getCurrent() {
        return Real.ZERO;
    }

    @Override
    public Real getPower() {
        return Real.ZERO;
    }

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
    public void stamp(RealMatrix y, RealVector j, RealVector x, int step, Map<String, Integer> nodeIndex) {
        int i = nodeIndex.get(fromNode);
        int k = nodeIndex.get(toNode);

        double g = 1.0 / resistance.asDouble();
        y.addToEntry(i, i, g);
        y.addToEntry(k, k, g);
        y.addToEntry(i, k, -g);
        y.addToEntry(k, i, -g);
    }

    // --- Legacy getters ---
    public String getFromNode() {
        return fromNode;
    }

    public String getToNode() {
        return toNode;
    }

    public Real getResistance() {
        return resistance;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public double getLength() {
        return length;
    }

    // --- New contract-facing getters ---
    public String getLineId() {
        return line_id;
    }

    public String getNodeFromId() {
        return node_from_id;
    }

    public String getNodeToId() {
        return node_to_id;
    }

    public String getTrackId() {
        return track_id;
    }

    @Override
    public String toString() {
        return String.format(
                "Line(%d->%d, R=%.6f, desc=%s, cat=%s, len=%.3f, lineId=%s, fromId=%s, toId=%s, trackId=%s)",
                fromNode,
                toNode,
                resistance.asDouble(),
                description,
                category,
                length,
                line_id,
                node_from_id,
                node_to_id,
                track_id
        );
    }
}