package org.supply.domain;

import java.util.Objects;

/** An internal cable bundle. Its physical length is independent of railway coordinates. */
public record CableConnection(String id, String fromTerminalId, String toTerminalId,
                              double lengthM, ConductorType conductorType, int multiplicity) {
    public CableConnection {
        if (id == null || id.isBlank() || fromTerminalId == null || fromTerminalId.isBlank()
                || toTerminalId == null || toTerminalId.isBlank() || fromTerminalId.equals(toTerminalId))
            throw new IllegalArgumentException("Cable requires an id and distinct terminal endpoints");
        Objects.requireNonNull(conductorType, "conductorType");
        if (!Double.isFinite(lengthM) || lengthM <= 0 || multiplicity < 1)
            throw new IllegalArgumentException("Cable length must be positive and finite; multiplicity must be >= 1: " + id);
        if (!Double.isFinite(lengthM / 1000.0 * conductorType.resistanceOhmPerKm() / multiplicity)
                || lengthM / 1000.0 * conductorType.resistanceOhmPerKm() / multiplicity <= 0)
            throw new IllegalArgumentException("Cable resistance is outside the supported range: " + id);
    }

    public double resistanceOhm() {
        return lengthM / 1000.0 * conductorType.resistanceOhmPerKm() / multiplicity;
    }
}
