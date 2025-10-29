package org.dcsim.electric;

// Fysik-hooks som solver och devices anropar
public interface PhysOps<T> {
    // Norton-ström: I = Y * (E − dV)  (Y kan vara reell (DC) eller komplex (AC))
    T nortonCurrent(T Y, T E, T dV);

    // Nät-effekt (till/från nät): P = Re{ I * conj(dV) }
    double netPower(T I, T dV, ScalarOps<T> ops);

    // “Riktningsenhet" för ström (ström går från hög till låg potential)
    // DC: sign(dV), AC: använd realdel av dV eller fasinvarianta regler – här en enkel DC-vänlig default
    T dirFromDv(T dV, ScalarOps<T> ops);

    // Stabilisering nära dV≈0 (undvik division med ~0)
    T safeDiv(T num, T den, ScalarOps<T> ops, double floor);
}
