package org.supply.domain;

import org.dcsim.electric.NodeKind;
import org.dcsim.math.FieldElement;
import org.dcsim.math.Real;

import java.util.Objects;

/**
 * A Node represents a point in the electric network with a voltage.
 * Devices are connected to nodes and inject/extract current based on voltage.
 */
public class Node<T extends FieldElement<T>> {
    private String description; // Optional descriptive text

    private final String node_id;
    private final int section_id;
    private Real voltage;
    private final String position;

    // v0.8 additions (MFE)
    private int trackId;           // -1 if not on a track
    private int position_m;      // numeric coordinate [m] along track

    // ?? Main constructor (the only "real" one)
    public Node(
            String node_id,
            int section_id,
            int position_m,
            String position
    ) {
        this.node_id = Objects.requireNonNull(node_id);
        this.section_id = section_id;
        this.position_m = position_m;
        this.position = position; // may be null in future if removed
        this.voltage = Real.ZERO;
    }

    public String getNode_id() {
        return node_id;
    }

    public Real getVoltage() {
        return voltage;
    }

    public String getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public int getTrackId() {
        return trackId;
    }

    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }

    public int getPositionM() {
        return position_m;
    }

    public void setPositionM(int positionM) {
        this.position_m = positionM;
    }

    public void setVoltage(Real voltage) {
        this.voltage = voltage;
    }


}
