package org.supply.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TrackLoaderTest {

    @Test
    public void keepsAscendingInfrastructureGeometryIndependentOfRouteDirection() {
        LoadedTrackModel track = new TrackLoader().load(
                List.of(
                        new KilometerBoard("101", "0+000", 0, 1000),
                        new KilometerBoard("101", "1+000", 1000, 1000),
                        new KilometerBoard("101", "2+000", 2000, 1000),
                        new KilometerBoard("102", "0+000", 0, 1000),
                        new KilometerBoard("102", "1+000", 1000, 1000),
                        new KilometerBoard("102", "2+000", 2000, 1000)
                ),
                List.of(),
                List.of()
        );

        assertSection(track.getSectionsById().get("101"), "101");
        assertSection(track.getSectionsById().get("102"), "102");
    }

    private static void assertSection(
            TrackSection section, String expectedSection
    ) {
        assertEquals(expectedSection, section.getSectionId());
        assertEquals(2, section.getSegments().size());

        RouteSegment first = section.getSegments().get(0);
        assertEquals(0, first.getStartModelM());
        assertEquals(1000, first.getLengthM());
        assertEquals(0, first.getStartRwy().getPositionM());
        assertEquals(1000, first.getEndRwy().getPositionM());

        RouteSegment second = section.getSegments().get(1);
        assertEquals(1000, second.getStartModelM());
        assertEquals(1000, second.getLengthM());
        assertEquals(1000, second.getStartRwy().getPositionM());
        assertEquals(2000, second.getEndRwy().getPositionM());
    }
}
