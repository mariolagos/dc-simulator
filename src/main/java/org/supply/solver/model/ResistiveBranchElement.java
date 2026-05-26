package org.supply.solver.model;

import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceStamp;

public record ResistiveBranchElement(
        String id,
        String fromNodeId,
        String toNodeId,
        Real resistanceOhm
) implements ElectricalElement {

    @Override
    public void stamp(AdmittanceStamp stamp) {
        double r = resistanceOhm.asDouble();

        if (r <= 0.0) {
            throw new IllegalArgumentException("Branch resistance must be positive: " + id);
        }

        double g = 1.0 / r;

        stamp.addConductance(fromNodeId, fromNodeId, g);
        stamp.addConductance(toNodeId, toNodeId, g);
        stamp.addConductance(fromNodeId, toNodeId, -g);
        stamp.addConductance(toNodeId, fromNodeId, -g);
    }
}