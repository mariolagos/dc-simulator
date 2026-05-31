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

        double currentA;

        if (requestedPowerW > 0.0) {

            double requestedCurrentA =
                    requestedPowerW / voltageV;

            currentA = Math.min(
                    requestedCurrentA,
                    tractionCurrentLimitA(voltageV)
            );

        } else if (requestedPowerW < 0.0) {

            double requestedCurrentA =
                    requestedPowerW / voltageV;

            currentA = Math.max(
                    requestedCurrentA,
                    regenerativeCurrentLimitA(voltageV)
            );

        } else {
            return;
        }
    }

    private static double tractionCurrentLimitA(double voltageV) {
        if (voltageV <= U_MIN2_V) {
            return 0.0;
        }

        if (voltageV < U_MIN1_V) {
            return I_TRAIN_MAX_A
                    * (voltageV - U_MIN2_V)
                    / (U_MIN1_V - U_MIN2_V);
        }

        return I_TRAIN_MAX_A;
    }



}