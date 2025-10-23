package org.dcsim.electric;


import java.util.Collection;

/** Abstraktion för elsystemets lösare. */
public interface ElectricSolver {
    GridResult solve(GridModel model, double timeSec, int timestep);

    default void setTrainAnchors(java.util.Collection<?> anchors, double dtSec) { /* no-op */ }

    // NYTT: skicka (trainId -> total effekt i W) direkt
    default void setTrainRequestedPower(java.util.Map<String, Double> requestedPowerW, double dtSec) { /* no-op */ }
}

