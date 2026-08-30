package org.supply.solver.model;

import org.supply.solver.electrical.AdmittanceStamp;

public final class RegenerativeTrainElement implements ElectricalElement {

    private final String trainId;
    private final String feedingNodeId;
    private final String returnNodeId;
    private final double requestedPowerW;
    private final double voltageV;
    private final double uStartV;
    private final double uMaxV;

    public RegenerativeTrainElement(
            String trainId,
            String feedingNodeId,
            String returnNodeId,
            double requestedPowerW,
            double voltageV,
            double uStartV,
            double uMaxV
    ) {
        this.trainId = trainId;
        this.feedingNodeId = feedingNodeId;
        this.returnNodeId = returnNodeId;
        this.requestedPowerW = requestedPowerW;
        this.voltageV = voltageV;
        this.uStartV = uStartV;
        this.uMaxV = uMaxV;
    }

    @Override
    public void stamp(AdmittanceStamp stamp) {

        if (voltageV < uStartV) {
            double powerW = Math.abs(requestedPowerW);

            double conductanceS =
                    powerW / (voltageV * voltageV);

            double nortonCurrentA =
                    2.0 * powerW / voltageV;

            stamp.addConductance(
                    feedingNodeId,
                    feedingNodeId,
                    conductanceS
            );
            stamp.addConductance(
                    returnNodeId,
                    returnNodeId,
                    conductanceS
            );
            stamp.addConductance(
                    feedingNodeId,
                    returnNodeId,
                    -conductanceS
            );
            stamp.addConductance(
                    returnNodeId,
                    feedingNodeId,
                    -conductanceS
            );

            stamp.addCurrent(
                    feedingNodeId,
                    nortonCurrentA
            );
            stamp.addCurrent(
                    returnNodeId,
                    -nortonCurrentA
            );

            return;
        }

        if (voltageV >= uMaxV) {
            return;
        }

        double fullCurrentA =
                Math.abs(requestedPowerW) / uStartV;

        double rRegenOhm =
                (uMaxV - uStartV) / fullCurrentA;

        double g =
                1.0 / rRegenOhm;

        double iNortonA =
                uMaxV / rRegenOhm;

        stamp.addConductance(
                feedingNodeId,
                feedingNodeId,
                g
        );

        stamp.addConductance(
                returnNodeId,
                returnNodeId,
                g
        );

        stamp.addConductance(
                feedingNodeId,
                returnNodeId,
                -g
        );

        stamp.addConductance(
                returnNodeId,
                feedingNodeId,
                -g
        );

        stamp.addCurrent(
                feedingNodeId,
                iNortonA
        );

        stamp.addCurrent(
                returnNodeId,
                -iNortonA
        );
    }

    public static double regenerativeCurrentA(
            double requestedPowerW,
            double voltageV,
            double uStartV,
            double uMaxV
    ) {
        double fullCurrentA =
                Math.abs(requestedPowerW) / uStartV;

        if (voltageV < uStartV) {
            return Math.abs(requestedPowerW) / voltageV;
        }

        if (voltageV >= uMaxV) {
            return 0.0;
        }

        return fullCurrentA
                * (uMaxV - voltageV)
                / (uMaxV - uStartV);
    }
}