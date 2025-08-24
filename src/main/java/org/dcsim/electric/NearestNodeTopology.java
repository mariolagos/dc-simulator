// org/dcsim/electric/NearestNodeTopology.java
package org.dcsim.electric;

import org.dcsim.utils.PositionUtils;
import java.util.*;

public final class NearestNodeTopology implements Topology {

    /** line → sorted (by meters) list of (nodeId, [line,km,m]) */
    private final Map<Integer, List<Entry>> byLine = new HashMap<>();

    private static final class Entry {
        final int nodeId;
        final int[] pos;       // [line, km, m]
        final double meters;   // km*1000 + m

        Entry(int nodeId, int[] pos) {
            this.nodeId = nodeId;
            this.pos = pos;
            this.meters = PositionUtils.toMeters(pos);
        }
    }

    /**
     * @param infraNodePositions  infrastructure nodes only: nodeId → "line km+meters".
     *                            EXCLUDE any dynamic/train-specific nodes from this map.
     */
    public NearestNodeTopology(Map<Integer, String> infraNodePositions) {
        if (infraNodePositions == null || infraNodePositions.isEmpty()) {
            throw new IllegalArgumentException("infraNodePositions is empty");
        }
        // Build per-line sorted arrays
        Map<Integer, List<Entry>> tmp = new HashMap<>();
        for (Map.Entry<Integer, String> e : infraNodePositions.entrySet()) {
            int id = e.getKey();
            int[] parsed = PositionUtils.parseFlexible(e.getValue());
            tmp.computeIfAbsent(parsed[0], k -> new ArrayList<>()).add(new Entry(id, parsed));
        }
        // Sort by meters within each line
        for (var kv : tmp.entrySet()) {
            var list = kv.getValue();
            list.sort(Comparator.comparingDouble(a -> a.meters));
            byLine.put(kv.getKey(), List.copyOf(list));
        }
    }

    @Override
    public int nearestInfraNodeId(int[] pos) {
        var line = pos[0];
        var list = byLine.get(line);
        if (list == null || list.isEmpty()) {
            // No nodes on this line -> fallback: search across all lines (should be rare)
            return nearestAcrossAll(pos);
        }
        double target = PositionUtils.toMeters(pos);
        int idx = lowerBound(list, target); // index of first node with meters >= target
        if (idx <= 0) return list.get(0).nodeId;
        if (idx >= list.size()) return list.get(list.size()-1).nodeId;

        // Pick closer of {idx-1, idx}
        Entry left  = list.get(idx-1);
        Entry right = list.get(idx);
        return Math.abs(target - left.meters) <= Math.abs(right.meters - target)
                ? left.nodeId : right.nodeId;
    }

    @Override
    public int[] bracketInfraNodeIds(int[] pos) {
        var line = pos[0];
        var list = byLine.get(line);
        if (list == null || list.isEmpty()) {
            int n = nearestAcrossAll(pos);
            return new int[] { n, n };
        }
        double target = PositionUtils.toMeters(pos);
        int idx = lowerBound(list, target);
        if (idx <= 0) {
            int n = list.get(0).nodeId;
            return new int[] { n, n };
        }
        if (idx >= list.size()) {
            int n = list.get(list.size()-1).nodeId;
            return new int[] { n, n };
        }
        // Strictly bracket: (left, right)
        return new int[] { list.get(idx-1).nodeId, list.get(idx).nodeId };
    }

    /** Binary search: first index s.t. entry.meters >= target. */
    private static int lowerBound(List<Entry> list, double target) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).meters < target) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private int nearestAcrossAll(int[] pos) {
        double bestD = Double.POSITIVE_INFINITY;
        int bestId = -1;
        double target = PositionUtils.toMeters(pos);
        for (var list : byLine.values()) {
            for (var e : list) {
                double d = Math.abs(e.meters - target);
                if (d < bestD) { bestD = d; bestId = e.nodeId; }
            }
        }
        if (bestId < 0) throw new IllegalStateException("No infrastructure nodes available");
        return bestId;
    }
}
