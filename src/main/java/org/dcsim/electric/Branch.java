package org.dcsim.electric;

/**
 * Represents a resistive branch between two nodes
 */
public class Branch {
    private final Node from;
    private final Node to;
    private final double resistance; // in ohms

    public Branch(Node from, Node to, double resistance) {
        this.from = from;
        this.to = to;
        this.resistance = resistance;
    }

    public Node getFrom() {
        return from;
    }

    public Node getTo() {
        return to;
    }

    public double getResistance() {
        return resistance;
    }
}
