package org.supply.solver.model;

import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceStamp;

public record CalculationBranch(
        String id,
        String sourceId,
        String fromNodeId,
        String toNodeId,
        Real resistanceOhm,
        boolean schematicConnector
) implements  ElectricalElement{

    /** Existing branches remain ordinary external/internal lines. */
    public CalculationBranch(String id, String sourceId, String fromNodeId,
                             String toNodeId, Real resistanceOhm) {
        this(id, sourceId, fromNodeId, toNodeId, resistanceOhm, false);
    }

    @Override
    public void stamp(AdmittanceStamp stamp) {
        double r = resistanceOhm().asDouble();

        if (r <= 0.0) {
            throw new IllegalArgumentException(
                    "Branch resistance must be positive: " + id()
            );
        }

        double g = 1.0 / r;

        stamp.addConductance(fromNodeId(), fromNodeId(), g);
        stamp.addConductance(toNodeId(), toNodeId(), g);

        stamp.addConductance(fromNodeId(), toNodeId(), -g);
        stamp.addConductance(toNodeId(), fromNodeId(), -g);
    }}