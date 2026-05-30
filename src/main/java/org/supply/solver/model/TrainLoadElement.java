package org.supply.solver.model;

import org.supply.solver.electrical.AdmittanceStamp;

public final class TrainLoadElement implements ElectricalElement {

    private final String feedingNodeId;
    private final String returnNodeId;

    private final double requestedPowerW;
    private final double voltageV;

    public TrainLoadElement(
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double voltageV
    ) {
        this.feedingNodeId = feedingNodeId;
        this.returnNodeId = returnNodeId;
        this.requestedPowerW = requestedPowerW;
        this.voltageV = voltageV;
    }

    @Override
    public void stamp(AdmittanceStamp stamp) {

        double p = requestedPowerW;
        double u = voltageV;

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