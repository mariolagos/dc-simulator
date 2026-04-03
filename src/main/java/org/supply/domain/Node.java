package org.supply.domain;

import org.dcsim.electric.Device;
import org.dcsim.electric.NodeKind;
import org.dcsim.math.FieldElement;
import org.dcsim.math.Real;
import org.supply.mapping.PositionUtils;


import java.util.List;
import java.util.Objects;

/**
 * A Node represents a point in the electric network with a voltage.
 * Devices are connected to nodes and inject/extract current based on voltage.
 */
public class Node<T extends FieldElement<T>> {
    private String description; // Optional descriptive text

    private final int internal_id;
    private final String node_id;
    private final int section_id;
    private Real voltage;
    private final String position;

    // v0.8 additions (MFE)
    private NodeKind nodeKind;     // SUBSTATION / TRAIN / GROUND
    private int trackId;           // -1 if not on a track
    private int position_m;      // numeric coordinate [m] along track

    // ?? Main constructor (the only "real" one)
    public Node(
            int internal_id,
            String node_id,
            int section_id,
            int position_m,
            String position
    ) {
        this.internal_id = internal_id;
        this.node_id = Objects.requireNonNull(node_id);
        this.section_id = section_id;
        this.position_m = position_m;
        this.position = position; // may be null in future if removed
        this.voltage = Real.ZERO;
    }

    // ?? Deprecated constructor 1
    @Deprecated
    public Node(int internal_id, Real voltage, String position) {

        this(
                internal_id,
                "legacy_" + internal_id,
                PositionUtils.parseFlexible(position)[0],
                PositionUtils.parseFlexible(position)[1],
                position
        );
    }

    // ?? Deprecated constructor 2
    @Deprecated
    public Node(int internal_id, Real voltage, String position, String description) {

        this(
                internal_id,
                description != null ? description : "legacy_" + internal_id,
                PositionUtils.parseFlexible(position)[0],
                PositionUtils.parseFlexible(position)[1],
                position
        );
    }

    public String getNode_id() {
        return node_id;
    }

    public String getNameOrDefault() {
        return node_id != null && !node_id.isBlank() ? node_id : "N" + internal_id;
    }

    public int get_internal_id() {
        return internal_id;
    }

    public Real getVoltage() {
        return voltage;
    }

    public String getPosition() {
        return position;
    }

    public Real computeNetCurrent(List<Device<Real>> devices, double time) {
        Real total = Real.ZERO;
        for (Device<Real> device : devices) {
            if (device.getConnectedNode() == internal_id) {
                total = total.plus(device.computeCurrent(voltage, time));
            }
        }
        return total;
    }

    public String getDescription() {
        return description;
    }

    public NodeKind getNodeKind() {
        return nodeKind;
    }

    public void setNodeKind(NodeKind nodeKind) {
        this.nodeKind = nodeKind;
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
