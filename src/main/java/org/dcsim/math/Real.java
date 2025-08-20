package org.dcsim.math;

import java.io.Serializable;
import java.math.BigDecimal;

public final class Real implements FieldElement<Real>, Comparable<Real>, Serializable {
    private final double value;

    public static final Real ZERO = new Real(0.0);
    public static final Real ONE = new Real(1.0);

    public Real(double value) {
        this.value = value;
    }

    public double asDouble() {
        return value;
    }

    public boolean isZero() {
        return value == 0.0;
    }

    public boolean isPositive() {
        return value > 0.0;
    }

    public boolean isNegative() {
        return value < 0.0;
    }

    public boolean gt(Real other) {
        return this.value > other.value;
    }

    public boolean lt(Real other) {
        return this.value < other.value;
    }

    public Real plus(Real other) {
        return new Real(this.value + other.value);
    }

    public Real minus(Real other) {
        return new Real(this.value - other.value);
    }

    public Real times(Real other) {
        return new Real(this.value * other.value);
    }

    public Real times(double scalar) {
        return new Real(this.value * scalar);
    }

    public Real divide(Real other) {
        if (other.value == 0.0) throw new ArithmeticException("Divide by zero");
        return new Real(this.value / other.value);
    }

    public Real divide(double scalar) {
        if (scalar == 0.0) throw new ArithmeticException("Divide by zero");
        return new Real(this.value / scalar);
    }

    public Real negate() {
        return new Real(-this.value);
    }

    public Real abs() {
        return new Real(Math.abs(this.value));
    }

    public Real reciprocal() {
        if (value == 0.0) throw new ArithmeticException("Reciprocal of zero");
        return new Real(1.0 / this.value);
    }

    @Override
    public Real zeroElement() {
        return ZERO;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Real)) return false;
        Real other = (Real) obj;
        return Double.compare(this.value, other.value) == 0;
    }

    @Override
    public int compareTo(Real other) {
        return Double.compare(this.value, other.value);
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    @Override
    public String toString() {
        return Double.toString(value);
    }

    // Optional: factory
    public static Real fromDouble(double value) {
        return new Real(value);
    }

    // --- Stub domain-specific accessors ---
    public double motoringPowerKW() {
        return value; // interpret value as motoring power in kW
    }

    public double brakingPowerKW() {
        return value; // interpret value as braking power in kW
    }

    public double positionMeters() {
        return value; // interpret value as position in meters
    }

    public Real signum() {
        double s = Math.signum(this.value);   // ger -1.0, 0.0 eller 1.0
        return new Real(s);
    }

    public boolean gte(Real other) {
        return this.value >= other.value;
    }

}
