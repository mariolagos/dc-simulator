package org.dcsim.electric;

import org.dcsim.math.Real;

public final class RealOps implements ScalarOps<Real> {
    public Real zero() { return Real.fromDouble(0); }
    public Real one()  { return Real.fromDouble(1); }
    public Real fromDouble(double x){ return Real.fromDouble(x); }
    public double toDouble(Real x){ return x.asDouble(); }

    public Real add(Real a, Real b){ return Real.fromDouble(a.asDouble()+b.asDouble()); }
    public Real sub(Real a, Real b){ return Real.fromDouble(a.asDouble()-b.asDouble()); }
    public Real mul(Real a, Real b){ return Real.fromDouble(a.asDouble()*b.asDouble()); }
    public Real div(Real a, Real b){ return Real.fromDouble(a.asDouble()/b.asDouble()); }
    public Real neg(Real a){ return Real.fromDouble(-a.asDouble()); }

    public double abs(Real a){ return Math.abs(a.asDouble()); }
    public double real(Real a){ return a.asDouble(); }
    public Real conj(Real a){ return a; }
}
