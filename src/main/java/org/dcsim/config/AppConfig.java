package org.dcsim.config;

/** Minimal config holder just to unblock Runner. Extend as needed later. */
public class AppConfig {

    private final SimulationControl simulationControl;

    public AppConfig(SimulationControl simulationControl) {
        this.simulationControl = simulationControl;
    }

    public SimulationControl getSimulationControl() {
        return simulationControl;
    }

    // --- nested minimal SimulationControl ---
    public static final class SimulationControl {
        private final double tickDuration;     // seconds
        private final String simulationStart;  // e.g. "2025-08-11T08:00:00+02:00" or "08:00:00"
        private final String simulationEnd;    // same formats accepted

        public SimulationControl(double tickDuration, String simulationStart, String simulationEnd) {
            this.tickDuration = tickDuration;
            this.simulationStart = simulationStart;
            this.simulationEnd = simulationEnd;
        }

        public double getTickDuration() { return tickDuration; }
        public String getSimulationStart() { return simulationStart; }
        public String getSimulationEnd() { return simulationEnd; }
    }
}
