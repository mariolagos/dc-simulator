package org.dcsim.utils;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

/** JUnit 4 tests: SectionChain.addDistance across section boundaries. */
public class SectionChainAddDistanceTest {

    private static PositionUtils.SectionChain chain() {
        List<PositionUtils.Section> secs = Arrays.asList(
                new PositionUtils.Section(10, 3_000.0),
                new PositionUtils.Section(11, 1_500.0),
                new PositionUtils.Section(12, 500.0)
        );
        return new PositionUtils.SectionChain(secs);
    }

    @Test
    public void addDistance_stays_within_section() {
        PositionUtils.SectionChain c = chain();
        PositionUtils.TrackPosition start = PositionUtils.TrackPosition.parse("10 1+200");
        PositionUtils.TrackPosition end = c.addDistance(start, 300.0);
        assertEquals("10 1+500", end.format());
    }

    @Test
    public void addDistance_wraps_to_next_section() {
        PositionUtils.SectionChain c = chain();
        PositionUtils.TrackPosition start = PositionUtils.TrackPosition.parse("10 2+900");
        PositionUtils.TrackPosition end = c.addDistance(start, 200.0);
        assertEquals("11 0+100", end.format());
    }

    @Test
    public void addDistance_skips_multiple_sections() {
        PositionUtils.SectionChain c = chain();
        PositionUtils.TrackPosition start = PositionUtils.TrackPosition.parse("10 2+800");
        PositionUtils.TrackPosition end = c.addDistance(start, 1_000.0);
        assertEquals("11 0+800", end.format());
    }

    @Test
    public void addDistance_clamps_at_chain_end() {
        PositionUtils.SectionChain c = chain();
        PositionUtils.TrackPosition start = PositionUtils.TrackPosition.parse("12 0+400");
        PositionUtils.TrackPosition end = c.addDistance(start, 500.0);
        assertEquals("12 0+500", end.format());
    }
}
