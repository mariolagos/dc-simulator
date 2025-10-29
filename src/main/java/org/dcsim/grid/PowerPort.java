// src/main/java/org/dcsim/grid/PowerPort.java
package org.dcsim.grid;

/**
 * Minimal DC power port for Train ↔ Grid coupling.
 * Convention: +P = consume from DC bus (traction), -P = inject to bus (regen).
 */
public interface PowerPort {
    /** Last measured DC bus voltage at this node [V]. */
    double getVoltage();

    /** Request active power at this node: +P=load, -P=source. */
    void setRequestedPower(double watts);

    /** Latest requested power (for tests/inspection). */
    double getRequestedPower();

    /** Optional: grid node id (return -1 if not applicable). */
    default int getNodeId() { return -1; }
}
