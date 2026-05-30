package org.supply.solver.model;

import org.supply.solver.electrical.AdmittanceStamp;

public final class TrainLoadElement implements ElectricalElement {

    private final String feedingNodeId;
    private final String returnNodeId;

    private final double requestedPowerW;
    private final double nominalVoltageV;

    public TrainLoadElement(
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double nominalVoltageV
    ) {
        this.feedingNodeId = feedingNodeId;
        this.returnNodeId = returnNodeId;
        this.requestedPowerW = requestedPowerW;
        this.nominalVoltageV = nominalVoltageV;
    }

    @Override
    public void stamp(AdmittanceStamp stamp) {

        double p = requestedPowerW;
        double u = nominalVoltageV;

        if (p > 0.0) {
            // traction: train consumes power
            double i = p / u;

            stamp.addCurrent(feedingNodeId, -i);
            stamp.addCurrent(returnNodeId, i);

        } else if (p < 0.0) {
            // regenerative braking: train injects power into DC network
            double i = -p / u;

            stamp.addCurrent(feedingNodeId, i);
            stamp.addCurrent(returnNodeId, -i);

        } else {
            // idle
            return;
        }
    }
}