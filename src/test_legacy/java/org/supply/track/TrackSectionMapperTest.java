package org.supply.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TrackSectionMapperTest {

    @Test
    public void shouldMapCoordinateAtSegmentStart() {
        TrackSection section = buildSection();

        TrackSectionMapper mapper = new TrackSectionMapper();

        ModelCoordinate mc = mapper.toModel(
                section,
                new RwyCoordinate("S1", "0+200", 200, null)
        );

        assertEquals("S1", mc.getSectionId());
        assertEquals(0, mc.getPositionM());
    }

    @Test
    public void shouldMapCoordinateInMiddleOfDecreasingSegment() {
        TrackSection section = buildSection();

        TrackSectionMapper mapper = new TrackSectionMapper();

        ModelCoordinate mc = mapper.toModel(
                section,
                new RwyCoordinate("S1", "0+150", 150, null)
        );

        assertEquals("S1", mc.getSectionId());
        assertEquals(50, mc.getPositionM());
    }

    @Test
    public void shouldMapCoordinateAtEndOfFirstSegment() {
        TrackSection section = buildSection();

        TrackSectionMapper mapper = new TrackSectionMapper();

        ModelCoordinate mc = mapper.toModel(
                section,
                new RwyCoordinate("S1", "0+100", 100, null)
        );

        assertEquals("S1", mc.getSectionId());
        assertEquals(100, mc.getPositionM());
    }

    @Test
    public void shouldMapCoordinateInSecondSegment() {
        TrackSection section = buildSection();

        TrackSectionMapper mapper = new TrackSectionMapper();

        ModelCoordinate mc = mapper.toModel(
                section,
                new RwyCoordinate("S1", "0+075", 75, null)
        );

        assertEquals("S1", mc.getSectionId());
        assertEquals(125, mc.getPositionM());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailWhenCoordinateSectionDoesNotMatch() {
        TrackSection section = buildSection();

        TrackSectionMapper mapper = new TrackSectionMapper();

        mapper.toModel(
                section,
                new RwyCoordinate("S2", "0+150", 150, null)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailWhenCoordinateIsOutsideSection() {
        TrackSection section = buildSection();

        TrackSectionMapper mapper = new TrackSectionMapper();

        mapper.toModel(
                section,
                new RwyCoordinate("S1", "0+300", 300, null)
        );
    }

    private TrackSection buildSection() {
        TrackLoader loader = new TrackLoader();

        List<KilometerBoard> boards = List.of(
                new KilometerBoard("S1", "0+200", 200, 100),
                new KilometerBoard("S1", "0+100", 100, 50),
                new KilometerBoard("S1", "0+050", 50, 0)
        );

        LoadedTrackModel model = loader.load(boards, List.of(), List.of());
        return model.getSectionsById().get("S1");
    }
}