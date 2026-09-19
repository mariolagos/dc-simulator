package org.supply.domain;

/**
 * Project-specific description of a delimited train log.
 * Position and power may be omitted when they can be derived respectively
 * from speed, or from voltage and current.
 */
public record RunLogFormat(
        char delimiter,
        RunColumn time,
        RunColumn position,
        RunColumn speed,
        RunColumn power,
        RunColumn voltage,
        RunColumn current,
        boolean consumptionPositive
) {
    public RunLogFormat {
        if (time == null) {
            throw new IllegalArgumentException("A log time column is required");
        }
        if (position == null && speed == null) {
            throw new IllegalArgumentException(
                    "A log position or speed column is required"
            );
        }
        if (power == null && (voltage == null || current == null)) {
            throw new IllegalArgumentException(
                    "A log power column or both voltage and current are required"
            );
        }
    }
}
