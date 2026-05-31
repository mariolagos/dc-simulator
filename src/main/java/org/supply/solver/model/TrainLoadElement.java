package org.supply.solver.model;

import org.supply.domain.SystemParameters;
import org.supply.solver.electrical.AdmittanceStamp;

public final class TrainLoadElement implements ElectricalElement {

    private final String feedingNodeId;
    private final String returnNodeId;

    private final double requestedPowerW;
    private final double voltageV;

    private final SystemParameters systemParameters;

    public TrainLoadElement(
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double voltageV,
            SystemParameters systemParameters
    ) {
        this.feedingNodeId = feedingNodeId;
        this.returnNodeId = returnNodeId;
        this.requestedPowerW = requestedPowerW;
        this.voltageV = voltageV;
        this.systemParameters = systemParameters;
    }

    @Override
    public void stamp(AdmittanceStamp stamp) {
        double currentA = trainCurrentA();

        if (currentA == 0.0) {
            return;
        }

        stamp.addCurrent(feedingNodeId, -currentA);
        stamp.addCurrent(returnNodeId, currentA);
    }

    private double trainCurrentA() {
        if (requestedPowerW > 0.0) {
            return tractionCurrentA(
                    requestedPowerW,
                    voltageV,
                    systemParameters
            );
        }

        if (requestedPowerW < 0.0) {
            return regenerativeCurrentA(
                    requestedPowerW,
                    voltageV,
                    systemParameters
            );
        }

        return 0.0;
    }

    private static double tractionCurrentA(
            double requestedPowerW,
            double voltageV,
            SystemParameters systemParameters
    ) {
        double requestedCurrentA =
                requestedPowerW / voltageV;

        return Math.min(
                requestedCurrentA,
                tractionCurrentLimitA(voltageV, systemParameters)
        );
    }

    private static double tractionCurrentLimitA(
            double voltageV,
            SystemParameters systemParameters
    ) {
        double uMin1V = systemParameters.uMin1V();
        double uMin2V = systemParameters.uMin2V();
        double iTrainMaxA = systemParameters.iTrainMaxA();

        if (voltageV <= uMin1V) {
            return 0.0;
        }

        if (voltageV < uMin2V) {
            return iTrainMaxA
                    * (voltageV - uMin1V)
                    / (uMin2V - uMin1V);
        }

        return iTrainMaxA;
    }

    private static double regenerativeCurrentA(
            double requestedPowerW,
            double voltageV,
            SystemParameters systemParameters
    ) {
        double requestedCurrentA =
                requestedPowerW / voltageV;

        return Math.max(
                requestedCurrentA,
                regenerativeCurrentLimitA(voltageV, systemParameters)
        );
    }

    private static double regenerativeCurrentLimitA(
            double voltageV,
            SystemParameters systemParameters
    ) {
        if (voltageV >= systemParameters.uMaxV()) {
            return 0.0;
        }

        return -systemParameters.iTrainMaxA();
    }
}