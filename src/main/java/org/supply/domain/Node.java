package org.supply.domain;

import org.dcsim.math.Real;

import java.util.Objects;

/**
 * Node in the supply network.
 *
 * positionRwy is the raw railway position string, for example:
 * "23 12+6 U"
 *
 * We keep it as a raw string for now and avoid mixing in parsed
 * section/track/meter fields until that model is settled.
 */
public class Node {

    private final String nodeId;
    private final String positionRwy;
    private Real voltage;
    private String description;

    public Node(String nodeId, String positionRwy) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.positionRwy = Objects.requireNonNull(positionRwy, "positionRwy");
        this.voltage = Real.ZERO;
    }

    public String getNodeId() {
        return nodeId;
    }

    // Behåll tillfälligt för bakåtkompatibilitet om annan kod redan använder detta namn.
    public String getNode_id() {
        return nodeId;
    }

    public String getPositionRwy() {
        return positionRwy;
    }

    // Behåll tillfälligt för bakåtkompatibilitet om annan kod redan använder detta namn.
    public String getPosition() {
        return positionRwy;
    }

    public Real getVoltage() {
        return voltage;
    }

    public void setVoltage(Real voltage) {
        this.voltage = Objects.requireNonNull(voltage, "voltage");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}