package org.supply.solver.model;

import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceStamp;
import org.supply.solver.io.LongTableWriter;

public record DiodeSubstationElement(
        String id,
        String feedingNodeId,
        String returnNodeId,
        Real emfV,
        Real internalResistanceOhm
) implements ElectricalElement {

    private static final double VOLTAGE_TOLERANCE_V = 1e-3;

    @Override
    public void stamp(AdmittanceStamp stamp) {
        Real feedingVoltage =
                stamp.previousVoltageOf(feedingNodeId);

        Real returnVoltage =
                stamp.previousVoltageOf(returnNodeId);

        if (feedingVoltage != null && returnVoltage != null) {
            double uTerminalV =
                    feedingVoltage.asDouble()
                            - returnVoltage.asDouble();

            boolean blocked =
                    uTerminalV > emfV.asDouble() + VOLTAGE_TOLERANCE_V;
            if (blocked) {
                return;
            }
        } else {
//            System.out.printf(
//                    "DSE %s no previous voltage -> CONDUCTING%n",
//                    id
//            );
        }

        double r = internalResistanceOhm.asDouble();

        if (r <= 0.0) {
            throw new IllegalArgumentException(
                    "Diode substation internal resistance must be positive: " + id
            );
        }

        double e = emfV.asDouble();
        double g = 1.0 / r;
        double i = e / r;

        stamp.addConductance(feedingNodeId, feedingNodeId, g);
        stamp.addConductance(returnNodeId, returnNodeId, g);
        stamp.addConductance(feedingNodeId, returnNodeId, -g);
        stamp.addConductance(returnNodeId, feedingNodeId, -g);

        stamp.addCurrent(feedingNodeId, i);
        stamp.addCurrent(returnNodeId, -i);
    }

    @Override
    public void saveStaticResult(LongTableWriter writer) {
        writer.signalRow(
                null,
                "DIODE_SUBSTATION",
                id,
                "feeding_node_id",
                feedingNodeId,
                "",
                "STATIC",
                null,
                null
        );

        writer.signalRow(
                null,
                "DIODE_SUBSTATION",
                id,
                "return_node_id",
                returnNodeId,
                "",
                "STATIC",
                null,
                null
        );

        writer.signalRow(
                null,
                "DIODE_SUBSTATION",
                id,
                "emf_V",
                emfV.asDouble(),
                "V",
                "STATIC",
                null,
                null
        );

        writer.signalRow(
                null,
                "DIODE_SUBSTATION",
                id,
                "internal_resistance_ohm",
                internalResistanceOhm.asDouble(),
                "ohm",
                "STATIC",
                null,
                null
        );

    }
}