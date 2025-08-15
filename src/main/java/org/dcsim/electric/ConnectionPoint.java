package org.dcsim.electric;

public class ConnectionPoint {
    private final String id;
    private final String position;    // "line km+meters"
    private final String description; // Optional

    public ConnectionPoint(String id, String position, String description) {
        this.id = id;
        this.position = position;
        this.description = description;
    }

    // Getters...
}
