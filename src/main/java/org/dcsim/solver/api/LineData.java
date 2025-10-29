package org.dcsim.solver.api;

public record LineData(
        String id,
        int a,            // kompakt nodindex
        int b,            // kompakt nodindex
        double r_ohm      // motstånd
) {}
