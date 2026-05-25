package org.supply.solver.electrical;

import org.supply.math.Real;

import java.util.List;
import java.util.Map;

public final class AdmittanceSystem {

    private final Map<String, Integer> nodeIndexById;
    private final List<String> nodeIds;
    private final Real[][] conductanceMatrix;
    private final Real[] currentVector;
    private final String referenceNodeId;

    public AdmittanceSystem(
            Map<String, Integer> nodeIndexById,
            List<String> nodeIds,
            Real[][] conductanceMatrix,
            Real[] currentVector, String referenceNodeId
    ) {
        this.nodeIndexById = nodeIndexById;
        this.nodeIds = nodeIds;
        this.conductanceMatrix = conductanceMatrix;
        this.currentVector = currentVector;
        this.referenceNodeId = referenceNodeId;
    }

    public Map<String, Integer> nodeIndexById() {
        return nodeIndexById;
    }

    public List<String> nodeIds() {
        return nodeIds;
    }

    public Real[][] conductanceMatrix() {
        return conductanceMatrix;
    }

    public Real[] currentVector() {
        return currentVector;
    }

    public int size() {
        return nodeIds.size();
    }

    public int nodeIndex(String nodeId) {
        Integer index = nodeIndexById.get(nodeId);

        if (index == null) {
            throw new IllegalArgumentException(
                    "Unknown admittance node id: " + nodeId
            );
        }

        return index;
    }

    public String referenceNodeId() {
        return referenceNodeId;
    }

    public int referenceNodeIndex() {
        return nodeIndex(referenceNodeId);
    }
}