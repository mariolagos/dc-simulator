package org.dcsim.electric;

// Abstrakta skalära operationer (räcker för DC/AC fysik)
public interface ScalarOps<T> {
    T zero(); T one();
    T fromDouble(double x);
    double toDouble(T x);

    T add(T a, T b);
    T sub(T a, T b);
    T mul(T a, T b);
    T div(T a, T b);
    T neg(T a);

    // För AC: magnitud, realdel, konjugat. DC implementerar dessa trivialt.
    double abs(T a);
    double real(T a);
    T conj(T a);
}