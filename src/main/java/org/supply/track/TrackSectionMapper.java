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

        java.util.List<RouteSegment> segments = section.getSegments(railwayCoordinate.getTrackId());
        for (RouteSegment segment : segments) {
            if (isWithinSegment(segment, railwayCoordinate)) {
                return new ModelCoordinate(
                        section.getSectionId(),
                        railwayCoordinate.getTrackId(),
                        interpolateModelPosition(segment, railwayCoordinate)
                );
            }
        }

        RouteSegment last =
                segments.get(
                        segments.size() - 1
                );

        throw new IllegalArgumentException(
                "Coordinate not found within section '"
                        + section.getSectionId()
                        + "': "
                        + railwayCoordinate
                        + "; last segment="
                        + last.getStartRwy()
                        + " -> "
                        + last.getEndRwy()
        );
    }

    private int interpolateModelPosition(
            RouteSegment segment,
            RwyCoordinate coordinate) {
        int startRwyM = segment.getStartRwy().getPositionM();
        int endRwyM = segment.getEndRwy().getPositionM();
        int railwaySpanM = Math.abs(endRwyM - startRwyM);

        if (railwaySpanM == 0) {
            if (segment.getLengthM() == 0) {
                return segment.getStartModelM();
            }
            throw new IllegalArgumentException(
                    "Cannot map a non-zero model segment from identical railway coordinates: "
                            + segment.getStartRwy()
                            + " -> "
                            + segment.getEndRwy()
            );
        }

        int railwayOffsetM = Math.abs(coordinate.getPositionM() - startRwyM);
        double fraction = (double) railwayOffsetM / railwaySpanM;
        return segment.getStartModelM()
                + (int) Math.round(fraction * segment.getLengthM());
    }

    private boolean isWithinSegment(RouteSegment segment, RwyCoordinate coordinate) {
        int start = segment.getStartRwy().getPositionM();
        int end = segment.getEndRwy().getPositionM();
        int pos = coordinate.getPositionM();

        int min = Math.min(start, end);
        int max = Math.max(start, end);

        return pos >= min && pos <= max;
    }


    public RwyCoordinate toRailway(
            TrackSection section,
            ModelCoordinate modelCoordinate
    ) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(modelCoordinate, "modelCoordinate");

        if (!section.getSectionId().equals(modelCoordinate.getSectionId())) {
            throw new IllegalArgumentException(
                    "Coordinate section does not match section "
                            + section.getSectionId());
        }

        for (RouteSegment segment :
                section.getSegments(modelCoordinate.getTrackId())) {

            int startModelM = segment.getStartModelM();
            int lengthM = segment.getLengthM();
            int positionM = modelCoordinate.getPositionM();

            if (positionM < startModelM
                    || positionM > startModelM + lengthM) {
                continue;
            }

            int startRwyM = segment.getStartRwy().getPositionM();
            int endRwyM = segment.getEndRwy().getPositionM();

            double fraction =
                    (double) (positionM - startModelM) / lengthM;

            int railwayM = (int) Math.round(
                    startRwyM + fraction * (endRwyM - startRwyM));

            return new RwyCoordinate(
                    section.getSectionId(),
                    RwyCoordinate.formatKmShort(railwayM),
                    railwayM,
                    modelCoordinate.getTrackId()
            );
        }

        throw new IllegalArgumentException(
                "Model position outside section: " + modelCoordinate);
    }
}
