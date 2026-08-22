package org.supply.solver.model;

import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceStamp;

public final class FixedLoadElement implements ElectricalElement {

    private final String id;
    private final String feedingNodeId;
    private final String returnNodeId;
    private final double powerW;

    public FixedLoadElement(
            String id,
            String feedingNodeId,
            String returnNodeId,
            double powerW
    ) {
        this.id = id;
        this.feedingNodeId = feedingNodeId;
        this.returnNodeId = returnNodeId;
        this.powerW = powerW;
    }

    @Override
    public void stamp(AdmittanceStamp stamp) {
        Real feedingVoltage =
                stamp.previousVoltageOf(feedingNodeId);

        Real returnVoltage =
                stamp.previousVoltageOf(returnNodeId);

        if (feedingVoltage == null || returnVoltage == null) {
            return;
        }

        Real voltage =
                feedingVoltage.minus(returnVoltage);

        double currentA =
                powerW / voltage.asDouble();

        stamp.addCurrent(feedingNodeId, -currentA);
        stamp.addCurrent(returnNodeId, currentA);
    }

    public String id() {
        return id;
    }

    public String feedingNodeId() {
        return feedingNodeId;
    }

    public String returnNodeId() {
        return returnNodeId;
    }

    public double powerW() {
        return powerW;
    }
}