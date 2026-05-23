package org.dcsim.unit.electric;

import org.dcsim.electric.DcLine;
import org.dcsim.electric.DynamicLineTopologyBuilder;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class DynamicLineTopologyBuilderOrderingTest {

    @Test
    public void buildDynamicLines_sorts_by_position_and_connects_consecutive_nodes() {
        // Unsorted on purpose
        List<DynamicLineTopologyBuilder.NodePos> nodes = Arrays.asList(
                new DynamicLineTopologyBuilder.NodePos("2", 1, 1500),
                new DynamicLineTopologyBuilder.NodePos("1", 1, 0),
                new DynamicLineTopologyBuilder.NodePos("3", 1, 3000),
                new DynamicLineTopologyBuilder.NodePos("99", 1, 2000)
        );

        List<DcLine> lines = DynamicLineTopologyBuilder.buildDynamicLines(
                nodes,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        ).stream().map(d -> (DcLine) d).collect(Collectors.toList());

        assertEquals("Expected N-1 lines", 3, lines.size());

        Set<String> pairs = lines.stream()
                .map(l -> pair(l.getFromNode(), l.getToNode()))
                .collect(Collectors.toSet());

        // Sorted order by position: 1 (0), 2 (1500), 99 (2000), 3 (3000)
        assertEquals(setOfPairs(
                pair("1", "2"),
                pair("2", "99"),
                pair("99", "3")
        ), pairs);
    }

    private static String pair(String a, String b) {
        return (a.compareTo(b) <= 0) ? a + "|" + b : b + "|" + a;
    }

    private static Set<String> setOfPairs(String... ps) {
        Set<String> s = new LinkedHashSet<>();
        for (String p : ps) s.add(p);
        return s;
    }
}