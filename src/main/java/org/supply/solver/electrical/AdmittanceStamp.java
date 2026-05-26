package org.supply.solver.electrical;

import org.supply.math.Real;

import java.util.Map;

public final class AdmittanceStamp {

    private final Real[][] g;
    private final Real[] j;
    private final Map<String, Integer> nodeIndexById;

    public AdmittanceStamp(
            Real[][] g,
            Real[] j,
            Map<String, Integer> nodeIndexById
    ) {
        this.g = g;
        this.j = j;
        this.nodeIndexById = nodeIndexById;
    }

    public void addConductance(String rowNodeId, String colNodeId, double value) {
        int row = nodeIndex(rowNodeId);
        int col = nodeIndex(colNodeId);

        double current = g[row][col].asDouble();
        g[row][col] = Real.fromDouble(current + value);
    }

    public void addCurrent(String nodeId, double value) {
        int row = nodeIndex(nodeId);

        double current = j[row].asDouble();
        j[row] = Real.fromDouble(current + value);
    }

    private int nodeIndex(String nodeId) {
        Integer index = nodeIndexById.get(nodeId);

        if (index == null) {
            throw new IllegalArgumentException("Unknown node id: " + nodeId);
        }

        return index;
    }
}