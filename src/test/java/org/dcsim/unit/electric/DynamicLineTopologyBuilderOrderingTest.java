package org.dcsim.unit.electric;

import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DynamicLineTopologyBuilderOrderingTest {

    @Test
    public void buildDynamicLines_sorts_by_position_and_connects_consecutive_nodes() {
        // Unsorted on purpose
        List<DynamicLineTopologyBuilder.NodePos> nodes = Arrays.asList(
                new DynamicLineTopologyBuilder.NodePos(2, 1, 1500),
                new DynamicLineTopologyBuilder.NodePos(1, 1, 0),
                new DynamicLineTopologyBuilder.NodePos(3, 1, 3000),
                new DynamicLineTopologyBuilder.NodePos(99, 1, 2000)
        );

        List<DcLine> lines = DynamicLineTopologyBuilder.buildDynamicLines(
                nodes,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        ).stream().map(d -> (DcLine) d).collect(Collectors.toList());

        assertEquals("Expected N-1 lines", 3, lines.size());

        Set<Long> pairs = lines.stream()
                .map(l -> pair(l.getFromNode(), l.getToNode()))
                .collect(Collectors.toSet());

        // Sorted order by position: 1 (0), 2 (1500), 99 (2000), 3 (3000)
        assertEquals(setOfPairs(
                pair(1, 2),
                pair(2, 99),
                pair(3, 99)
        ), pairs);
    }

    private static long pair(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return (((long) lo) << 32) ^ (hi & 0xffffffffL);
    }

    private static Set<Long> setOfPairs(long... ps) {
        Set<Long> s = new LinkedHashSet<>();
        for (long p : ps) s.add(p);
        return s;
    }
}
