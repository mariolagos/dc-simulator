package org.dcsim.solver.build;

import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;

import java.util.*;

/** Builds a straight single-track DC net with evenly spaced nodes. */
public final class StraightTrackBuilder {

    private StraightTrackBuilder() {}

    public static final class SubCfg {
        public final String id;
        public final int nodeIndex;     // index in [0..nNodes-1]
        public final double emf_V;
        public final double rint_ohm;
        public final boolean allowBackfeed;
        public SubCfg(String id, int nodeIndex, double emf_V, double rint_ohm, boolean allowBackfeed) {
            this.id=id; this.nodeIndex=nodeIndex; this.emf_V=emf_V; this.rint_ohm=rint_ohm; this.allowBackfeed=allowBackfeed;
        }
    }

    public static final class TrainCfg {
        public final String id;
        public final int a;
        public final int b;
        public final double req_W;
        public final double iMax_A;
        public final double cut_V;
        public final double vmax_V;
        public TrainCfg(String id, int a, int b, double req_W, double iMax_A, double cut_V, double vmax_V) {
            this.id=id; this.a=a; this.b=b; this.req_W=req_W; this.iMax_A=iMax_A; this.cut_V=cut_V; this.vmax_V=vmax_V;
        }
    }

    /**
     * Build a straight single-track DC net:
     * - nNodes equally spaced along length_km
     * - line R = r_per_km * segment_length_km between consecutive nodes
     * - substations connected nodeIndex -> groundIndex
     * - trains connected between explicit node indices a,b
     */
    public static DcNet build(
            int groundIndex,
            int nNodes,
            double length_km,
            double r_per_km,
            List<SubCfg> subs,
            List<TrainCfg> trains
    ) {
        if (nNodes < 2) throw new IllegalArgumentException("nNodes >= 2 required");
        if (groundIndex < 0 || groundIndex >= nNodes) throw new IllegalArgumentException("groundIndex out of range");

        final double segKm = length_km / (nNodes - 1);

        // Node ids 0..nNodes-1
        List<Integer> nodeIds = new ArrayList<>(nNodes);
        for (int i=0;i<nNodes;i++) nodeIds.add(i);

        // id -> compact index map
        Map<Integer,Integer> idxById = new HashMap<>(nNodes * 2);
        for (int i=0;i<nNodes;i++) idxById.put(nodeIds.get(i), i);

        // Lines between consecutive nodes
        List<LineData> lines = new ArrayList<>(Math.max(0, nNodes - 1));
        for (int i=0;i<nNodes-1;i++) {
            double R = r_per_km * segKm;
            lines.add(new LineData("L_"+i+"_"+(i+1), i, i+1, R));
        }

        // Substations
        List<SubstationData> ss = new ArrayList<>(subs != null ? subs.size() : 0);
        if (subs != null) {
            for (SubCfg c : subs) {
                if (c.nodeIndex < 0 || c.nodeIndex >= nNodes)
                    throw new IllegalArgumentException("Substation nodeIndex out of range: " + c.nodeIndex);
                ss.add(new SubstationData(c.id, c.nodeIndex, groundIndex, c.emf_V, c.rint_ohm, c.allowBackfeed));
            }
        }

        // Trains
        List<TrainData> trs = new ArrayList<>(trains != null ? trains.size() : 0);
        if (trains != null) {
            for (TrainCfg t : trains) {
                if (t.a < 0 || t.a >= nNodes || t.b < 0 || t.b >= nNodes)
                    throw new IllegalArgumentException("Train nodes out of range: " + t.a + "," + t.b);
                trs.add(new TrainData(t.id, t.a, t.b, t.req_W, t.iMax_A, t.cut_V, t.vmax_V));
            }
        }

        return new DcNet(
                nNodes,
                groundIndex,
                Collections.unmodifiableList(nodeIds),
                Collections.unmodifiableMap(idxById),
                Collections.unmodifiableList(lines),
                Collections.unmodifiableList(ss),
                Collections.unmodifiableList(trs)
        );
    }
}
