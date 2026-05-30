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

        double currentA =
                requestedPowerW
                        / nominalVoltageV;

        stamp.addCurrent(
                feedingNodeId,
                -currentA
        );

        stamp.addCurrent(
                returnNodeId,
                currentA
        );
    }
}