package org.supply.domain;

/**
 * Maps one normalized run quantity to a source column.
 *
 * @param name exact column heading in the source file
 * @param unit source unit, for example {@code m/s}, {@code km/h}, {@code W},
 *             {@code kW}, {@code V} or {@code A}
 * @param format optional date/time pattern; empty for numeric columns
 */
public record RunColumn(String name, String unit, String format) {
    public RunColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Run column name must not be blank");
        }
        unit = unit == null ? "" : unit.trim();
        format = format == null ? "" : format;
    }
}
