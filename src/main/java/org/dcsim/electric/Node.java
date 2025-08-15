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

    public Node(int id, Real voltage, String position) {
        this.id = id;
        this.voltage = voltage;
        this.position = position;
    }
    public Node(int id, Real voltage, String position, String description) {
        this(id, voltage, position);
        this.description = description;
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
}
