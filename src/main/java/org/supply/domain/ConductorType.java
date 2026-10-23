package org.supply.domain;

/** Resistance of one conductor, at the temperature represented by the input data. */
public record ConductorType(String id, double resistanceOhmPerKm) {
    public ConductorType {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Conductor type needs an id");
        if (!Double.isFinite(resistanceOhmPerKm) || resistanceOhmPerKm <= 0)
            throw new IllegalArgumentException("Conductor resistance must be positive and finite: " + id);
    }
}
