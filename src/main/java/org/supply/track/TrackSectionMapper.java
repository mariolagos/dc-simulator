package org.supply.track;

import java.util.Objects;

/**
 * Maps railway coordinates to model coordinates within a single section.
 */
public final class TrackSectionMapper {

    public ModelCoordinate toModel(TrackSection section, RwyCoordinate railwayCoordinate) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(railwayCoordinate, "railwayCoordinate");

        if (!section.getSectionId().equals(railwayCoordinate.getSectionId())) {
            throw new IllegalArgumentException(
                    "Coordinate section '" + railwayCoordinate.getSectionId()
                            + "' does not match section '" + section.getSectionId() + "'"
            );
        }

        for (RouteSegment segment : section.getSegments()) {
            if (isWithinSegment(segment, railwayCoordinate)) {
                int offsetInSegment = Math.abs(railwayCoordinate.getPositionM() - segment.getStartRwy().getPositionM());
                int modelPositionM = segment.getStartModelM() + offsetInSegment;
                return new ModelCoordinate(section.getSectionId(), modelPositionM);
            }
        }

        throw new IllegalArgumentException(
                "Coordinate not found within section '" + section.getSectionId() + "': " + railwayCoordinate
        );
    }

    private boolean isWithinSegment(RouteSegment segment, RwyCoordinate coordinate) {
        int start = segment.getStartRwy().getPositionM();
        int end = segment.getEndRwy().getPositionM();
        int pos = coordinate.getPositionM();

        int min = Math.min(start, end);
        int max = Math.max(start, end);

        return pos >= min && pos <= max;
    }
}