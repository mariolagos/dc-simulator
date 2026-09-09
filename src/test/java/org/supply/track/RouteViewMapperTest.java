package org.supply.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class RouteViewMapperTest {

    @Test
    public void mapsWeRouteAcrossTrackSections() {
        RouteView route =
                new RouteView(
                        "RED-WE",
                        "W-E",
                        List.of(
                                sample(0, "101", 12_400, "U"),
                                sample(6_349, "101", 18_749, "U"),
                                sample(6_350, "200", 0, "U1"),
                                sample(12_549, "200", 6_199, "U1"),
                                sample(12_550, "102", 41_300, "U"),
                                sample(19_350, "102", 48_100, "U")
                        )
                );

        RouteViewMapper mapper = new RouteViewMapper();

        assertCoordinate(
                mapper.toRailway(route, 6_349),
                "101",
                "U",
                18_749
        );

        assertCoordinate(
                mapper.toRailway(route, 6_350),
                "200",
                "U1",
                0
        );

        assertCoordinate(
                mapper.toRailway(route, 12_549),
                "200",
                "U1",
                6_199
        );

        assertCoordinate(
                mapper.toRailway(route, 12_550),
                "102",
                "U",
                41_300
        );
    }

    private static PathSample sample(
            double routePositionM,
            String sectionId,
            int railwayPositionM,
            String trackId
    ) {
        return new PathSample(
                routePositionM,
                new RwyCoordinate(
                        sectionId,
                        RwyCoordinate.formatKmShort(railwayPositionM),
                        railwayPositionM,
                        trackId
                )
        );
    }

    private static void assertCoordinate(
            RwyCoordinate actual,
            String expectedSectionId,
            String expectedTrackId,
            int expectedPositionM
    ) {
        assertEquals(
                expectedSectionId,
                actual.getSectionId()
        );
        assertEquals(
                expectedTrackId,
                actual.getTrackId()
        );
        assertEquals(
                expectedPositionM,
                actual.getPositionM()
        );
    }
}