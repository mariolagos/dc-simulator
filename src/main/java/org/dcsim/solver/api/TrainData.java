package org.dcsim.solver.api;

/**
 * Data för ett tåg i DC-nätet.
 * Accessornamn matchar testerna: req_W(), imax_A(), vmin_V(), vmax_V().
 */
public record TrainData(
        String id,
        int a,
        int b,
        double req_W,    // +W = motor; -W = regen
        double imax_A,   // max abs-ström
        double vmin_V,    // cutoff för motor/regengating
        double vmax_V    // max spänning för linjeregen
) {}
