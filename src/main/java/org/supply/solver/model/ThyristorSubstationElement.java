package org.supply.solver.model;

import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceStamp;

public record ThyristorSubstationElement(
        String id,
        String feedingNodeId,
        String returnNodeId,
        Real emfV,
        Real internalResistanceOhm
) implements ElectricalElement {

    @Override
    public void stamp(AdmittanceStamp stamp) {
        double r = internalResistanceOhm.asDouble();

        if (r == 0.0) {
            throw new IllegalArgumentException(
                    "Thyristor substation internal resistance must be non zero: " + id
            );
        }

        double e = emfV.asDouble();
        double g = Math.max(1.0 / r, 0.);
        double i = Math.max(e / r, 0.);

        // Internal resistance
        stamp.addConductance(feedingNodeId, feedingNodeId, g);
        stamp.addConductance(returnNodeId, returnNodeId, g);
        stamp.addConductance(feedingNodeId, returnNodeId, -g);
        stamp.addConductance(returnNodeId, feedingNodeId, -g);

        // Norton equivalent current source
        stamp.addCurrent(feedingNodeId, i);
        stamp.addCurrent(returnNodeId, -i);
    }
}