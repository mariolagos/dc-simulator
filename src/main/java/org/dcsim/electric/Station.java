package org.dcsim.electric;

public class Station {
    private final String name;        // Full name (e.g., "Alto Flute")
    private final String abbreviation; // Short code (e.g., "AFL")
    private final String position;    // "line km+meters" format
    private final String description; // Optional description

    public Station(String name, String abbreviation, String position, String description) {
        this.name = name;
        this.abbreviation = abbreviation;
        this.position = position;
        this.description = description;
    }

    // Getters...
}
