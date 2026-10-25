package org.supply.solver.model;

import org.supply.solver.electrical.AdmittanceStamp;

/** Processed by node reduction before stamping, never by a large conductance. */
public record IdealConnectionElement(String id, String fromNodeId, String toNodeId) implements ElectricalElement {
    @Override public void stamp(AdmittanceStamp stamp) {
        throw new IllegalStateException("Ideal connection must be reduced before stamping");
    }
}
