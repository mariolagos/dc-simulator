package org.dcsim.unit.preprocessing;

import org.junit.Test;

import java.util.*;
import static org.junit.Assert.*;

public class TrackRunCoordinateJoinTest {

    /** Track row: source-of-truth mapping position_m -> (section, bisKm, bisMeter, nodeLabel) */
    static final class TrackRow {
        final int section;       // 1,2,3
        final double pos_m;      // ABS meters (the only truth)
        final int bisKm;
        final int bisMeter;
        final String node;       // e.g. "1-2" or "2-6" etc

        TrackRow(int section, double pos_m, int bisKm, int bisMeter, String node) {
            this.section = section;
            this.pos_m = pos_m;
            this.bisKm = bisKm;
            this.bisMeter = bisMeter;
            this.node = node;
        }
    }

    static final class RunRow {
        final Integer section;   // nullable: missing => ambiguity test
        final double pos_m;

        RunRow(Integer section, double pos_m) {
            this.section = section;
            this.pos_m = pos_m;
        }
    }

    static final class JoinResult {
        final int section;
        final double pos_m;
        final double bisPos_m;   // derived from (bisKm*1000 + bisMeter), interpolated
        final TrackRow left;
        final TrackRow right;

        JoinResult(int section, double pos_m, double bisPos_m, TrackRow left, TrackRow right) {
            this.section = section;
            this.pos_m = pos_m;
            this.bisPos_m = bisPos_m;
            this.left = left;
            this.right = right;
        }
    }

    // ---------- Join logic (the thing we want to freeze with a test) ----------

    static JoinResult join(List<TrackRow> track, RunRow run) {
        // Candidates: either filtered by section (preferred), or all (to detect junction ambiguity)
        List<TrackRow> candidates = new ArrayList<>();
        for (TrackRow r : track) {
            if (run.section == null || r.section == run.section) candidates.add(r);
        }

        // If section not given and multiple rows have same pos_m => ambiguous junction
        if (run.section == null) {
            int matches = 0;
            for (TrackRow r : candidates) if (r.pos_m == run.pos_m) matches++;
            if (matches > 1) {
                throw new IllegalArgumentException("Ambiguous junction at pos_m=" + run.pos_m
                        + " (multiple track sections share same position). Require section/trackId.");
            }
        }

        // Sort by pos
        candidates.sort(Comparator.comparingDouble(a -> a.pos_m));

        // Find bracketing points
        TrackRow left = null, right = null;
        for (int i = 0; i < candidates.size(); i++) {
            TrackRow r = candidates.get(i);
            if (r.pos_m <= run.pos_m) left = r;
            if (r.pos_m >= run.pos_m) { right = r; break; }
        }
        if (left == null || right == null)
            throw new IllegalArgumentException("Run pos out of track range: pos_m=" + run.pos_m);

        // Exact hit
        if (left.pos_m == right.pos_m) {
            double bisPos = left.bisKm * 1000.0 + left.bisMeter;
            return new JoinResult(left.section, run.pos_m, bisPos, left, right);
        }

        // Linear interpolation of bisPos (OK because your fake points are on half-km steps)
        double t = (run.pos_m - left.pos_m) / (right.pos_m - left.pos_m);
        double leftBis = left.bisKm * 1000.0 + left.bisMeter;
        double rightBis = right.bisKm * 1000.0 + right.bisMeter;
        double bisPos = leftBis + t * (rightBis - leftBis);

        // Section is taken from filtered set (run.section), or from left if section missing
        int sec = (run.section != null) ? run.section : left.section;
        return new JoinResult(sec, run.pos_m, bisPos, left, right);
    }

    // ---------- Fake data ----------

    static List<TrackRow> fakeTrack() {
        // Section 1: positions every 0.5 km-ish, junctions at 2.5 km and 3.5 km
        // We model ABS meters; bisKm/bisMeter align with ABS meters in this fake.
        return List.of(
                // section 1 (orange)
                new TrackRow(1,    0, 0,   0, "1-0"),
                new TrackRow(1,  500, 0, 500, "1-1"),
                new TrackRow(1, 2000, 2,   0, "1-2"),
                new TrackRow(1, 3000, 3,   0, "1-3"),
                new TrackRow(1, 4000, 4,   0, "1-4"),

                // section 2 (vertical up from junction at 2500 m)
                new TrackRow(2, 2500, 2, 500, "2-6"),
                new TrackRow(2, 3000, 3,   0, "2-7"),
                new TrackRow(2, 3500, 3, 500, "2-8"),
                new TrackRow(2, 4000, 4,   0, "2-9"),
                new TrackRow(2, 4500, 4, 500, "2-10"),

                // section 3 (vertical down from junction at 3500 m)
                new TrackRow(3, 3500, 3, 500, "3-12"),
                new TrackRow(3, 3000, 3,   0, "3-11"),
                new TrackRow(3, 2500, 2, 500, "3-10"),
                new TrackRow(3, 2000, 2,   0, "3-9"),
                new TrackRow(3, 1500, 1, 500, "3-8")
        );
    }

    // ---------- Tests ----------

    @Test
    public void interpolates_within_section1() {
        List<TrackRow> track = fakeTrack();

        // 2250 m on section 1 => between 2000 and 3000 => expected bisPos = 2250
        JoinResult r = join(track, new RunRow(1, 2250));

        assertEquals(1, r.section);
        assertEquals(2000.0, r.left.pos_m, 0.0);
        assertEquals(3000.0, r.right.pos_m, 0.0);
        assertEquals(2250.0, r.bisPos_m, 1e-9);
    }

    @Test
    public void junction_is_ambiguous_without_section() {
        List<TrackRow> track = fakeTrack();

        // 2500 exists on section 2 and section 3 (and junction conceptually) => must require section.
        try {
            join(track, new RunRow(null, 2500));
            fail("Expected ambiguity at junction pos=2500 without section");
        } catch (IllegalArgumentException e) {
            // ok
            assertTrue(e.getMessage().toLowerCase().contains("ambiguous"));
        }
    }

    @Test
    public void junction_is_resolved_with_section() {
        List<TrackRow> track = fakeTrack();

        JoinResult r2 = join(track, new RunRow(2, 2500));
        assertEquals(2, r2.section);
        assertEquals(2500.0, r2.bisPos_m, 1e-9);
        assertEquals("2-6", r2.left.node);

        JoinResult r3 = join(track, new RunRow(3, 2500));
        assertEquals(3, r3.section);
        assertEquals(2500.0, r3.bisPos_m, 1e-9);
        assertEquals("3-10", r3.left.node);
    }
}
