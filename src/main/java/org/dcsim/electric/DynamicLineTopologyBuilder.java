package org.dcsim.electric;

import org.dcsim.math.Real;

import java.util.*;

public final class DynamicLineTopologyBuilder {

    private DynamicLineTopologyBuilder() {}

    public static final class NodePos {
        public final String nodeId;
        public final int trackId;
        public final int posM;

        public NodePos(String nodeId, int trackId, int posM) {
            this.nodeId = nodeId;
            this.trackId = trackId;
            this.posM = posM;
        }
    }

    @FunctionalInterface
    public interface ResistanceFn {
        double compute(int trackId, int posA_m, int posB_m);
    }

    public static List<Device<Real>> buildDynamicLines(
            List<NodePos> nodes,
            ResistanceFn resistanceFn
    ) {
        Map<Integer, List<NodePos>> byTrack = new HashMap<>();
        for (NodePos n : nodes) {
            byTrack.computeIfAbsent(n.trackId, k -> new ArrayList<>()).add(n);
        }

        List<Device<Real>> out = new ArrayList<>();

        for (Map.Entry<Integer, List<NodePos>> e : byTrack.entrySet()) {
            int trackId = e.getKey();
            List<NodePos> nodesOnTrack = e.getValue();
            if (nodesOnTrack.size() < 2) continue;

            nodesOnTrack.sort(Comparator.comparingInt(a -> a.posM));

            for (int i = 0; i < nodesOnTrack.size() - 1; i++) {
                NodePos left  = nodesOnTrack.get(i);
                NodePos right = nodesOnTrack.get(i + 1);

                double R = resistanceFn.compute(trackId, left.posM, right.posM);

                out.add(new DcLine(
                        left.nodeId,
                        right.nodeId,
                        Real.fromDouble(R)
                ));
            }
        }

        return out;
    }
}
