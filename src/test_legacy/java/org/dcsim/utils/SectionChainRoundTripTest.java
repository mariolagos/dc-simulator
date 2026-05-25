package org.dcsim.utils;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

/** JUnit 4 tests: SectionChain toAbsolute/fromAbsolute round-trips. */
public class SectionChainRoundTripTest {

    private static PositionUtils.SectionChain chain() {
        List<PositionUtils.Section> secs = Arrays.asList(
                new PositionUtils.Section(1, 2_500.0),
                new PositionUtils.Section(2, 1_000.0),
                new PositionUtils.Section(3, 3_400.0)
        );
        return new PositionUtils.SectionChain(secs);
    }

    @Test
    public void toAbsolute_within_first_section() {
        PositionUtils.SectionChain c = chain();
        PositionUtils.TrackPosition tp = new PositionUtils.TrackPosition(1, 1, 200.0);
        double abs = c.toAbsolute(tp);
        assertEquals(1_200.0, abs, 1e-9);
    }

    @Test
    public void toAbsolute_into_second_section() {
        PositionUtils.SectionChain c = chain();
        PositionUtils.TrackPosition tp = new PositionUtils.TrackPosition(2, 0, 300.0);
        double abs = c.toAbsolute(tp);
        assertEquals(2_800.0, abs, 1e-9);
    }

    @Test
    public void fromAbsolute_edges_and_middle() {
        PositionUtils.SectionChain c = chain();
        assertEquals("1 0+000", c.fromAbsolute(0.0).format());
        assertEquals("1 2+500", c.fromAbsolute(2_500.0).format());
        assertEquals("1 2+500", c.fromAbsolute(2_500.0).format());
        assertEquals("3 0+900", c.fromAbsolute(2_500.0 + 1_000.0 + 900.0).format());
    }

    @Test
    public void round_trip_absolute_position() {
        PositionUtils.SectionChain c = chain();
        PositionUtils.TrackPosition tp = PositionUtils.TrackPosition.parse("3 1+050");
        double abs = c.toAbsolute(tp);
        PositionUtils.TrackPosition back = c.fromAbsolute(abs);
        assertEquals(tp.format(), back.format());
    }
}
