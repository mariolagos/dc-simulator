package org.supply.math;

public interface FieldElement<T> {
    T plus(T other);

    T minus(T other);

    T times(T other);

    T divide(T other);

    T negate();

    T reciprocal();

    boolean isZero();

    /**
     * Return the additive identity (zero) for this field.
     */
    T zeroElement();
}
