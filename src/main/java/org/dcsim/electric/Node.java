package org.dcsim.electric;

import org.dcsim.math.FieldElement;
import org.dcsim.math.Real;

import java.util.List;

/**
 * A Node represents a point in the electric network with a voltage.
 * Devices are connected to nodes and inject/extract current based on voltage.
 */
public class Node<T extends FieldElement<T>> {
    private String description; // Optional descriptive text

    private final int id;
    private final Real voltage;
    private final String position;
    private String name;

    // v0.8 additions (MFE)
    private NodeKind nodeKind;     // SUBSTATION / TRAIN / GROUND
    private int trackId;           // -1 if not on a track
    private int positionM;      // numeric coordinate [m] along track

    public Node(int id, Real voltage, String position) {
        this.id = id;
        this.voltage = voltage;
        this.position = position;

        // reasonable defaults:
        this.nodeKind = NodeKind.GROUND;
        this.trackId = -1;
        this.positionM = -1;
    }

    public Node(int id, Real voltage, String position, String description) {
        this(id, voltage, position);
        this.description = description;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameOrDefault() {
        return name != null && !name.isBlank() ? name : "N" + id;
    }

    public int getId() {
        return id;
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
            if (device.getConnectedNode() == id) {
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
        return positionM;
    }

    public void setPositionM(int positionM) {
        this.positionM = positionM;
    }

}
