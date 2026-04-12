package org.dcsim.solver.api;

import java.util.*;

/**
 * Immutable DC network snapshot used by the solver layer.
 * Transitional API:
 *  - Public fields remain (deprecated) so existing code keeps compiling.
 *  - New code should use accessor methods (n(), groundIndex(), ...).
 */
public final class DcNet {

    // ===== Transitional public fields (deprecated) =====
    @Deprecated public final int n;
    @Deprecated public final int groundIndex;
    @Deprecated public final List<String> nodeIds;
    @Deprecated public final Map<String, Integer> indexById;
    @Deprecated public final List<LineData> lines;
    @Deprecated public final List<SubstationData> substations;
    @Deprecated public final List<TrainData> trains;

    // ===== Canonical constructor with defensive copies and validation =====
    public DcNet(
            int n,
            int groundIndex,
            List<String> nodeIds,
            Map<String, Integer> indexById,
            List<LineData> lines,
            List<SubstationData> substations,
            List<TrainData> trains
    ) {
        // basic null checks
        Objects.requireNonNull(nodeIds, "nodeIds");
        Objects.requireNonNull(indexById, "indexById");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(substations, "substations");
        Objects.requireNonNull(trains, "trains");

        // defensive copies for immutability
        this.n            = n;
        this.groundIndex  = groundIndex;
        this.nodeIds      = List.copyOf(nodeIds);
        this.indexById    = Map.copyOf(indexById);
        this.lines        = List.copyOf(lines);
        this.substations  = List.copyOf(substations);
        this.trains       = List.copyOf(trains);

        // minimal validation
        if (n <= 0) {
            throw new IllegalArgumentException("n must be > 0");
        }
        if (this.nodeIds.size() != n) {
            throw new IllegalArgumentException("nodeIds size must equal n");
        }
        if (groundIndex < 0 || groundIndex >= n) {
            throw new IllegalArgumentException("groundIndex out of range");
        }
        // ensure indexById covers all nodeIds
        for (String id : this.nodeIds) {
            Integer ix = this.indexById.get(id);
            if (ix == null || ix < 0 || ix >= n) {
                throw new IllegalArgumentException("indexById missing or out of range for nodeId " + id);
            }
        }
        // quick bounds check for device endpoints
        for (LineData L : this.lines) {
            if (L.a() < 0 || L.a() >= n || L.b() < 0 || L.b() >= n) {
                throw new IllegalArgumentException("Line " + L.id() + " endpoints out of range");
            }
        }
        for (SubstationData ss : this.substations) {
            if (ss.a() < 0 || ss.a() >= n || ss.b() < 0 || ss.b() >= n) {
                throw new IllegalArgumentException("Substation " + ss.id() + " endpoints out of range");
            }
        }
        for (TrainData tr : this.trains) {
            if (tr.a() < 0 || tr.a() >= n || tr.b() < 0 || tr.b() >= n) {
                throw new IllegalArgumentException("Train " + tr.id() + " endpoints out of range");
            }
        }
    }

    // ===== Accessor methods (preferred) =====
    public int n() { return n; }
    public int groundIndex() { return groundIndex; }
    public List<String> nodeIds() { return nodeIds; }
    public Map<String, Integer> indexById() { return indexById; }
    public List<LineData> lines() { return lines; }
    public List<SubstationData> substations() { return substations; }
    public List<TrainData> trains() { return trains; }

    // ===== Small utility often handy in builders/tests =====
    public static Map<Integer, Integer> indexMapFromIds(List<Integer> ids) {
        Objects.requireNonNull(ids, "ids");
        Map<Integer, Integer> out = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i++) {
            out.put(ids.get(i), i);
        }
        return out;
    }

    // --- lägg i org.dcsim.solver.api.DcNet ---
    public Integer tryIdxOf(String nodeId) {
        return indexById.get(nodeId);
    }

    public int idxOf(String nodeId) {
        final Integer k = indexById.get(nodeId);
        if (k == null) {
            throw new IllegalArgumentException("Unknown nodeId: " + nodeId);
        }
        return k;
    }
}
