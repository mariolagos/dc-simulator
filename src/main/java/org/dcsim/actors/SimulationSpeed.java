package org.dcsim.actors;

public enum SimulationSpeed {
    FAST,       // no wall clock: run as fast as you can
    REAL_TIME   // one tick per (tickDurationSec) in wall time
}
