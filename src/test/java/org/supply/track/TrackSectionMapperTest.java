package org.supply.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class TrackSectionMapperTest {

    private final TrackSectionMapper mapper = new TrackSectionMapper();

    @Test
    public void scalesRailwayDistanceToShorterModelSegment() {
        TrackSection section = section(
                coordinate(45_000),
                coordinate(46_000),
                3_900,
                800
        );

        assertPosition(4_300, mapper.toModel(section, coordinate(45_500)));
        assertPosition(4_686, mapper.toModel(section, coordinate(45_983)));
        assertPosition(4_700, mapper.toModel(section, coordinate(46_000)));
    }

    @Test
    public void scalesRailwayDistanceToLongerModelSegment() {
        TrackSection section = section(
                coordinate(44_000),
                coordinate(45_000),
                2_700,
                1_200
        );

        assertPosition(3_300, mapper.toModel(section, coordinate(44_500)));
        assertPosition(3_900, mapper.toModel(section, coordinate(45_000)));
    }

    @Test
    public void mapsDecreasingRailwayCoordinates() {
        TrackSection section = section(
                coordinate(46_000),
                coordinate(45_000),
                3_900,
                800
        );

        assertPosition(3_900, mapper.toModel(section, coordinate(46_000)));
        assertPosition(4_300, mapper.toModel(section, coordinate(45_500)));
        assertPosition(4_700, mapper.toModel(section, coordinate(45_000)));
    }

    @Test
    public void mapsModelPositionToRailwayCoordinate() {
        TrackSection section = section(
                coordinate(45_000),
                coordinate(46_000),
                3_900,
                800
        );

        assertRailwayPosition(45_000,
                mapper.toRailway(section, modelCoordinate(3_900)));
        assertRailwayPosition(45_500,
                mapper.toRailway(section, modelCoordinate(4_300)));
        assertRailwayPosition(46_000,
                mapper.toRailway(section, modelCoordinate(4_700)));
    }

    @Test
    public void mapsModelPositionToDecreasingRailwayCoordinates() {
        TrackSection section = section(
                coordinate(46_000),
                coordinate(45_000),
                3_900,
                800
        );

        assertRailwayPosition(46_000,
                mapper.toRailway(section, modelCoordinate(3_900)));
        assertRailwayPosition(45_500,
                mapper.toRailway(section, modelCoordinate(4_300)));
        assertRailwayPosition(45_000,
                mapper.toRailway(section, modelCoordinate(4_700)));
    }

    @Test
    public void mapsDifferentTracksIndependently() {
        TrackSection section = new TrackSection(
                "102",
                List.of(
                        new RouteSegment(
                                0, coordinate(45_000),
                                coordinate(46_000), 0, 800),
                        new RouteSegment(
                                1, coordinateOnTrack(50_000, "D"),
                                coordinateOnTrack(52_000, "D"), 0, 800)
                )
        );

        assertRailwayPosition(45_500,
                mapper.toRailway(section,
                        new ModelCoordinate("102", "U", 400)));

        assertRailwayPosition(51_000,
                mapper.toRailway(section,
                        new ModelCoordinate("102", "D", 400)));
    }

    private static TrackSection section(
            RwyCoordinate start,
            RwyCoordinate end,
            int startModelM,
            int lengthM) {
        return new TrackSection(
                "102",
                List.of(new RouteSegment(0, start, end, startModelM, lengthM))
        );
    }

    private static RwyCoordinate coordinate(int positionM) {
        return new RwyCoordinate(
                "102",
                RwyCoordinate.formatKmShort(positionM),
                positionM,
                "U"
        );
    }

    private static void assertPosition(int expected, ModelCoordinate actual) {
        assertEquals("102", actual.getSectionId());
        assertEquals("U", actual.getTrackId());
        assertEquals(expected, actual.getPositionM());
    }

    private static ModelCoordinate modelCoordinate(int positionM) {
        return new ModelCoordinate("102", "U", positionM);
    }

    private static RwyCoordinate coordinateOnTrack(
            int positionM, String trackId) {
        return new RwyCoordinate(
                "102",
                RwyCoordinate.formatKmShort(positionM),
                positionM,
                trackId
        );
    }

    private static void assertRailwayPosition(
            int expected, RwyCoordinate actual) {
        assertEquals("102", actual.getSectionId());
        assertEquals(expected, actual.getPositionM());
    }
}
