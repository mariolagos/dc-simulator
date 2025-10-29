package org.dcsim.power;


import org.dcsim.math.Real;

import java.util.OptionalDouble;

/** A time series for traction power, with optional kinematics (x,v). */
public interface PowerProfile {
    /** Net traction power at time t (W). Positive = motoring, negative = regen. */
    Real getPowerAtTime(double tSec);

    /**
     * Absolute position (m) at time t, if available; otherwise null.
     */
    default OptionalDouble getPositionAtTime(double tSec) { return null; }

    /**
     * Speed (m/s) at time t, if available; otherwise null.
     */
    default OptionalDouble getSpeedAtTime(double tSec) { return null; }


}
