package org.supply.domain;

import org.supply.math.Real;

import java.util.Objects;

public class Line {

    private final String lineId;
    private final Node fromNode;
    private final Node toNode;
    private final Real resistanceOhmPerM;
    private String description;

    public Line(
            String lineId,
            Node fromNode,
            Node toNode,
            Real resistanceOhmPerM
    ) {
        this.lineId = Objects.requireNonNull(lineId, "lineId");
        this.fromNode = Objects.requireNonNull(fromNode, "fromNode");
        this.toNode = Objects.requireNonNull(toNode, "toNode");
        this.resistanceOhmPerM =
                Objects.requireNonNull(resistanceOhmPerM, "resistanceOhmPerM");
    }

    public Node getFromNode() {
        return fromNode;
    }

    public Node getToNode() {
        return toNode;
    }

    public Real getResistanceOhmPerM() {
        return resistanceOhmPerM;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}