package org.supply.solver.model;

import org.supply.domain.SystemParameters;
import org.supply.solver.electrical.AdmittanceStamp;

public final class TrainLoadElement implements ElectricalElement {

    private final String trainId;

    private final String feedingNodeId;
    private final String returnNodeId;

    private final double requestedPowerW;
    private final double voltageV;

    private final SystemParameters systemParameters;

    private static final double REGEN_DERATING_START_V = 850.0;

    public TrainLoadElement(
            String trainId,
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double voltageV,
            SystemParameters systemParameters
    ) {
        this.trainId = trainId;
        this.feedingNodeId = feedingNodeId;
        this.returnNodeId = returnNodeId;
        this.requestedPowerW = requestedPowerW;
        this.voltageV = voltageV;
        this.systemParameters = systemParameters;
    }

    @Override
    public void stamp(AdmittanceStamp stamp) {
        double currentA = currentA();

        if (currentA == 0.0) {
            return;
        }

        stamp.addCurrent(feedingNodeId, -currentA);
        stamp.addCurrent(returnNodeId, currentA);
    }

    public double currentA() {
        return trainCurrentA();
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
        double uMinV = systemParameters.uMinV();
        double uCutoffV = systemParameters.uCutoffV();
        double iTrainMaxA = systemParameters.iMaxA();

        if (voltageV <= uMinV) {
            return 0.0;
        }

        if (voltageV < uCutoffV) {
            return iTrainMaxA
                    * (voltageV - uMinV)
                    / (uCutoffV - uMinV);
        }

        return iTrainMaxA;
    }

//    private static double regenerativeCurrentA(
//            double requestedPowerW,
//            double voltageV,
//            SystemParameters systemParameters
//    ) {
//        double requestedCurrentA =
//                requestedPowerW / voltageV;
//
//        return Math.max(
//                requestedCurrentA,
//                regenerativeCurrentLimitA(voltageV, systemParameters)
//        );
//    }

    private static double regenerativeCurrentA(
            double requestedPowerW,
            double voltageV,
            SystemParameters systemParameters
    ) {
        double networkPowerW =
                requestedPowerW
                        * regenerativeNetworkFraction(
                        voltageV,
                        systemParameters
                );

        double requestedCurrentA =
                networkPowerW / voltageV;

        return Math.max(
                requestedCurrentA,
                -systemParameters.iMaxA()
        );
    }

    private static double regenerativeNetworkFraction(
            double voltageV,
            SystemParameters systemParameters
    ) {
        double uMaxV = systemParameters.uMaxV();

        if (voltageV <= REGEN_DERATING_START_V) {
            return 1.0;
        }

        if (voltageV >= uMaxV) {
            return 0.0;
        }

        return (uMaxV - voltageV)
                / (uMaxV - REGEN_DERATING_START_V);
    }
    public String feedingNodeId() {
        return feedingNodeId;
    }

    public String returnNodeId() {
        return returnNodeId;
    }

    public double requestedPowerW() {
        return requestedPowerW;
    }

    public String trainId() {
        return trainId;
    }
}