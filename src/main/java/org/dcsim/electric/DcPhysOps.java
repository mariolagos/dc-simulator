package org.dcsim.electric;


import org.dcsim.math.Real;

public final class DcPhysOps implements PhysOps<Real> {
    @Override
    public Real nortonCurrent(Real Y, Real E, Real dV) {
        double y = Y.asDouble(), e = E.asDouble(), dv = dV.asDouble();
        return Real.fromDouble(y * (e - dv));
    }

    @Override
    public double netPower(Real I, Real dV, ScalarOps<Real> ops) {
        return I.asDouble() * dV.asDouble(); // DC: P = I*V
    }

    @Override
    public Real dirFromDv(Real dV, ScalarOps<Real> ops) {
        double dv = dV.asDouble();
        return Real.fromDouble((Math.abs(dv) < 1e-12) ? 1.0 : Math.signum(dv));
    }

    @Override
    public Real safeDiv(Real num, Real den, ScalarOps<Real> ops, double floor) {
        double n = num.asDouble(), d = den.asDouble();
        double ad = Math.max(Math.abs(d), floor);
        return Real.fromDouble(n / ad);
    }
}
