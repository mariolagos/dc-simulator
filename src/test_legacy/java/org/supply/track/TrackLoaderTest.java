package org.supply.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TrackLoaderTest {

    @Test
    public void shouldBuildSingleSectionWithTwoSegments() {
        TrackLoader loader = new TrackLoader();

        List<KilometerBoard> boards = List.of(
                new KilometerBoard("S1", "0+000", 0, 120),
                new KilometerBoard("S1", "0+120", 120, 80),
                new KilometerBoard("S1", "0+200", 200, 0)
        );

        LoadedTrackModel model = loader.load(boards, List.of(), List.of());

        TrackSection section = model.getSectionsById().get("S1");
        assertNotNull(section);
        assertEquals(2, section.getSegments().size());

        RouteSegment first = section.getSegments().get(0);
        RouteSegment second = section.getSegments().get(1);

        assertEquals(0, first.getSequenceNo());
        assertEquals(0, first.getStartModelM());
        assertEquals(120, first.getLengthM());

        assertEquals(1, second.getSequenceNo());
        assertEquals(120, second.getStartModelM());
        assertEquals(80, second.getLengthM());
    }

    @Test
    public void shouldPreserveKilometerBoardOrderAndNotSort() {
        TrackLoader loader = new TrackLoader();

        List<KilometerBoard> boards = List.of(
                new KilometerBoard("S1", "0+200", 200, 50),
                new KilometerBoard("S1", "0+050", 50, 70),
                new KilometerBoard("S1", "0+120", 120, 0)
        );

        LoadedTrackModel model = loader.load(boards, List.of(), List.of());

        TrackSection section = model.getSectionsById().get("S1");

        RouteSegment first = section.getSegments().get(0);
        RouteSegment second = section.getSegments().get(1);

        assertEquals("0+200", first.getStartRwy().getPositionText());
        assertEquals("0+050", first.getEndRwy().getPositionText());

        assertEquals("0+050", second.getStartRwy().getPositionText());
        assertEquals("0+120", second.getEndRwy().getPositionText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailWhenTooFewBoards() {
        TrackLoader loader = new TrackLoader();

        List<KilometerBoard> boards = List.of(
                new KilometerBoard("S1", "0+000", 0, 0)
        );

        loader.load(boards, List.of(), List.of());
    }

    @Test
    public void shouldHandleDecreasingCoordinates() {
        TrackLoader loader = new TrackLoader();

        List<KilometerBoard> boards = List.of(
                new KilometerBoard("S1", "0+200", 200, 100),
                new KilometerBoard("S1", "0+100", 100, 50),
                new KilometerBoard("S1", "0+050", 50, 0)
        );

        LoadedTrackModel model = loader.load(boards, List.of(), List.of());
        TrackSection section = model.getSectionsById().get("S1");

        TrackSectionMapper mapper = new TrackSectionMapper();

        ModelCoordinate mc = mapper.toModel(
                section,
                new RwyCoordinate("S1", "0+150", 150, null)
        );

        assertEquals(50, mc.getPositionM());
    }
}