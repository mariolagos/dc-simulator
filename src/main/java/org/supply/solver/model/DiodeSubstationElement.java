package org.supply.solver.model;

import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceStamp;

public record DiodeSubstationElement(
        String id,
        String feedingNodeId,
        String returnNodeId,
        Real emfV,
        Real internalResistanceOhm
) implements ElectricalElement {

    @Override
    public void stamp(AdmittanceStamp stamp) {
        double r = internalResistanceOhm.asDouble();

        if (r <= 0.0) {
            throw new IllegalArgumentException(
                    "Diode substation internal resistance must be positive: " + id
            );
        }

        double e = emfV.asDouble();
        double g = 1.0 / r;
        double i = e / r;

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