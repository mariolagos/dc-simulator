package org.supply.track;

import java.util.Objects;

/**
 * Named station used for timetable and reporting.
 */
public final class Station {

    private final String name;
    private final RwyCoordinate position;

    public Station(String name, RwyCoordinate position) {
        this.name = Objects.requireNonNull(name, "name");
        this.position = Objects.requireNonNull(position, "position");
    }

    public String getName() {
        return name;
    }

    public RwyCoordinate getPosition() {
        return position;
    }
}