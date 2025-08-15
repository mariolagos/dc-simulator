package org.dcsim.electric;

import java.util.List;

public class Track {
    private final List<Station> stations;
    private final List<Node> nodes;
    private final List<Line> lines;
    private final List<Substation> substations;
    private final List<ConnectionPoint> connectionPoints;

    public Track(List<Station> stations,
                 List<Node> nodes,
                 List<Line> lines,
                 List<Substation> substations,
                 List<ConnectionPoint> connectionPoints) {
        this.stations = stations;
        this.nodes = nodes;
        this.lines = lines;
        this.substations = substations;
        this.connectionPoints = connectionPoints;
    }

    // Getters...
}
