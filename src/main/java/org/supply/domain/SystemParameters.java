package org.supply.domain;

import org.supply.solver.io.LongTableWriter;

public record SystemParameters(
        double uNominalV,
        double uMinV,
        double uCutoffV,
        double uMaxV,
        double iMaxA,
        double pTrainMaxW,
        double pAllowW
) {
    public void saveStaticData(LongTableWriter writer) {
        writer.signalRow(
                null,
                "SYSTEM",
                "DC",
                "u_nominal_V",
                uNominalV,
                "V",
                "STATIC",
                null,
                null
        );
        writer.signalRow(
                null,
                "SYSTEM",
                "DC",
                "u_min_V",
                uMinV,
                "V",
                "STATIC",
                null,
                null
        );
        writer.signalRow(
                null,
                "SYSTEM",
                "DC",
                "u_cutoff_V",
                uCutoffV,
                "V",
                "STATIC",
                null,
                null
        );
        writer.signalRow(
                null,
                "SYSTEM",
                "DC",
                "u_max_V",
                uMaxV,
                "V",
                "STATIC",
                null,
                null
        );

    }
}
