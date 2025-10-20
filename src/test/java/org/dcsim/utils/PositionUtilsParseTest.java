package org.dcsim.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/** JUnit 4 tests: parsing & formatting for s km+m coordinates. */
public class PositionUtilsParseTest {

    @Test
    public void parse_strict_normalizes_over_1000m() {
        int[] p = PositionUtils.parseStrict("3 12+1005");
        assertArrayEquals(new int[]{3, 13, 5}, p);
    }

    @Test
    public void parse_flexible_accepts_decimal_km_truncates_meters() {
        int[] p = PositionUtils.parseFlexible("3 12.345 km");
        assertArrayEquals(new int[]{3, 12, 345}, p);
    }

    @Test
    public void trackposition_parse_and_format_truncates_and_zero_pads() {
        PositionUtils.TrackPosition tp = PositionUtils.TrackPosition.parse("2 7+009.99");
        assertEquals("2 7+009", tp.format());
    }

    @Test
    public void normalize_rolls_1000m_into_km() {
        PositionUtils.TrackPosition tp = new PositionUtils.TrackPosition(1, 5, 1000.0).normalized();
        assertEquals(1, tp.line);
        assertEquals(6, tp.km);
        assertEquals(0.0, tp.m, 1e-9);
    }
}
