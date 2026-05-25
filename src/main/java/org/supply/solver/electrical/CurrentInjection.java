package org.supply.solver.electrical;

import org.supply.math.Real;

public final class CurrentInjection {

    private final String nodeId;
    private final Real currentAmpere;

    public CurrentInjection(String nodeId, Real currentAmpere) {
        this.nodeId = nodeId;
        this.currentAmpere = currentAmpere;
    }

    public String nodeId() {
        return nodeId;
    }

    public Real currentAmpere() {
        return currentAmpere;
    }
}