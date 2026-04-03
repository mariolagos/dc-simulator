package org.dcsim.contracts;

import org.dcsim.PowerPoint;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.utils.PositionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

public final class ContractChecks {
    private ContractChecks() {}

    public static void validateGridModel(GridModel<?> model) {
        Objects.requireNonNull(model, "model");

        // --- Ground must exist
        int gnd = model.getGroundNodeId();
        Node<?> g = model.nodeOrThrow(gnd);
        if (g == null) {
            throw new IllegalArgumentException("Ground node missing: nodeId=" + gnd);
        }
        // v0.8: do NOT require special trackId/position markers for ground.
        // Ground must simply be excluded from dynamic topology construction and line endpoints,
        // which is validated elsewhere (topology tests + line checks below).

        // --- Nodes: absolute meters, non-negative (except ground)

        Map<Integer, Integer> min = new HashMap<>();
        Map<Integer, Integer> max = new HashMap<>();        for (Node<?> n : model.getNodes()) {
            if (n.get_internal_id() == gnd || isAnchorNode(n)) continue;

            if (n.getTrackId() < 0) {
                throw new IllegalArgumentException("Node has invalid trackId < 0: nodeId=" + n.get_internal_id());
            }
            if (n.getPositionM() < 0) {
                throw new IllegalArgumentException("Node has invalid positionM < 0 (meters): nodeId=" + n.get_internal_id()
                        + " position=" + n.getPosition());
            }
            int track = n.getTrackId();
            int m = n.getPositionM();

            min.merge(track, m, Math::min);
            max.merge(track, m, Math::max);
        }

        // --- Lines: endpoints exist, no ground in dynamic part, and same track (v0.8)
        for (Object dev : model.getLines()) {
            Line l = (Line) dev;

            int a = l.getFromNode();
            int b = l.getToNode();

            if (a == gnd || b == gnd) {
                throw new IllegalArgumentException("Line must not connect directly to ground in grid.lines. from=" + a + " to=" + b);
            }

            Node<?> na = model.nodeOrThrow(a);
            Node<?> nb = model.nodeOrThrow(b);

            if (na.getTrackId() != nb.getTrackId()) {
                throw new IllegalArgumentException("Line crosses trackId boundary (v0.8 forbids this). from=" + a
                        + " track=" + na.getTrackId() + " to=" + b + " track=" + nb.getTrackId());
            }

            if (l.getResistance().asDouble() <= 0.0) {
                throw new IllegalArgumentException("Line resistance must be > 0. from=" + a + " to=" + b
                        + " R=" + l.getResistance().asDouble());
            }
        }
    }

    // ---------- Track extent ----------

    public record TrackExtent(int minM, int maxM) {
        public TrackExtent {
            if (minM < 0 || maxM < 0 || maxM < minM) {
                throw new IllegalArgumentException(
                        "Invalid TrackExtent: [" + minM + ", " + maxM + "]");
            }
        }
        public boolean contains(int m) { return m >= minM && m <= maxM; }
    }

    // ---------- Helpers ----------

    /** Convert boundary TrackPosition -> absolute meters (truncated). */
    public static int toAbsMeters(PositionUtils.TrackPosition tp) {
        if (tp == null) {
            throw new IllegalArgumentException("TrackPosition is null");
        }
        double abs = tp.normalized().metersInLine();
        if (!Double.isFinite(abs)) {
            throw new IllegalArgumentException("Non-finite metersInLine(): " + abs);
        }
        // v0.8 rule: truncate, do not round
        return (int) Math.floor(abs);
    }

    // ---------- Track contracts ----------

    /** v0.8: absolute meters >= 0, strictly increasing PER LINE. */
    public static void validateTrackPositions(List<PositionUtils.TrackPosition> pts) {
        Objects.requireNonNull(pts, "pts");
        if (pts.isEmpty()) {
            throw new IllegalArgumentException("Track dataset is empty");
        }

        // group by line
        Map<Integer, List<PositionUtils.TrackPosition>> byLine = new LinkedHashMap<>();
        for (PositionUtils.TrackPosition tp : pts) {
            byLine.computeIfAbsent(tp.line, k -> new ArrayList<>()).add(tp);
        }

        for (var e : byLine.entrySet()) {
            int line = e.getKey();
            List<PositionUtils.TrackPosition> list = e.getValue();

            list.sort(Comparator.comparingDouble(p -> p.normalized().metersInLine()));

            int prev = Integer.MIN_VALUE;
            for (int i = 0; i < list.size(); i++) {
                int m = toAbsMeters(list.get(i));
                if (m < 0) {
                    throw new IllegalArgumentException(
                            "Negative track meters at line=" + line + " i=" + i + ": " + m);
                }
                if (m <= prev) {
                    throw new IllegalArgumentException(
                            "Track meters not strictly increasing at line=" + line
                                    + " i=" + i + " prev=" + prev + " curr=" + m);
                }
                prev = m;
            }
        }
    }

    public static TrackExtent extentOf(List<PositionUtils.TrackPosition> pts) {
        validateTrackPositions(pts);
        // extent across all lines (v0.8: OK)
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (PositionUtils.TrackPosition tp : pts) {
            int m = toAbsMeters(tp);
            min = Math.min(min, m);
            max = Math.max(max, m);
        }
        return new TrackExtent(min, max);
    }

    // ---------- Run / Power contracts ----------

    /**
     * v0.8:
     * - time is finite and monotonic (non-decreasing)
     * - position absolute meters >= 0 and within track extent
     * - power is finite
     */
    public static void validateRunPoints(
            List<PowerPoint> pts,
            TrackExtent extent,
            ToDoubleFunction<PowerPoint> timeS,
            ToDoubleFunction<PowerPoint> powerW
    ) {
        Objects.requireNonNull(pts, "pts");
        Objects.requireNonNull(extent, "extent");
        if (pts.isEmpty()) {
            throw new IllegalArgumentException("Run dataset is empty");
        }

        double prevT = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < pts.size(); i++) {
            PowerPoint p = Objects.requireNonNull(pts.get(i), "PowerPoint[" + i + "]");

            double t = timeS.applyAsDouble(p);
            if (!Double.isFinite(t)) {
                throw new IllegalArgumentException("Non-finite time at i=" + i + ": " + t);
            }
            if (t < prevT) {
                throw new IllegalArgumentException(
                        "Run time not monotonic at i=" + i + ": prevT=" + prevT + " t=" + t);
            }
            prevT = t;

            PositionUtils.TrackPosition tp =
                    PositionUtils.parseFlexibleTP(p.positionString()).normalized();
            int m = toAbsMeters(tp);
            if (m < 0) {
                throw new IllegalArgumentException("Negative run meters at i=" + i + ": " + m);
            }
            if (!extent.contains(m)) {
                throw new IllegalArgumentException(
                        "Run position outside track extent at i=" + i
                                + ": posM=" + m + " extent=[" + extent.minM + "," + extent.maxM + "]");
            }

            double pw = powerW.applyAsDouble(p);
            if (!Double.isFinite(pw)) {
                throw new IllegalArgumentException("Non-finite power at i=" + i + ": " + pw);
            }
        }
    }

    public static Map<Integer, TrackExtent> extentByTrackFromModel(GridModel<?> model) {
        Objects.requireNonNull(model, "model");
        int gnd = model.getGroundNodeId();

        Map<Integer, Integer> min = new HashMap<>();
        Map<Integer, Integer> max = new HashMap<>();

        for (var n : model.getNodes()) {
            if (n.get_internal_id() == gnd || isAnchorNode(n)) continue;
            int track = n.getTrackId();
            int m = n.getPositionM();

            min.merge(track, m, Math::min);
            max.merge(track, m, Math::max);
        }

        Map<Integer, TrackExtent> out = new HashMap<>();
        for (var e : min.entrySet()) {
            int track = e.getKey();
            out.put(track, new TrackExtent(min.get(track), max.get(track)));
        }
        return out;
    }

    public static void validateRunPointsAgainstModelExtent(
            List<PowerPoint> pts,
            Map<Integer, TrackExtent> extentByTrack
    ) {
        if (pts == null || pts.isEmpty()) {
            throw new IllegalArgumentException("Run dataset is empty");
        }

        double prevT = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < pts.size(); i++) {
            PowerPoint p = Objects.requireNonNull(pts.get(i), "PowerPoint[" + i + "]");

            double t = p.time();   // (eller din getter)
            if (!Double.isFinite(t)) throw new IllegalArgumentException("Non-finite time at i=" + i + ": " + t);
            if (t < prevT) throw new IllegalArgumentException("Run time not monotonic at i=" + i + ": prevT=" + prevT + " t=" + t);
            prevT = t;

            var tp = PositionUtils.parseFlexibleTP(p.positionString()).normalized();
            int track = tp.line;
            int m = (int) Math.floor(tp.metersInLine());

            TrackExtent ex = extentByTrack.get(track);
            if (ex == null) {
                throw new IllegalArgumentException("Run references unknown trackId/line=" + track + " at i=" + i);
            }
            if (!ex.contains(m)) {
                throw new IllegalArgumentException("Run position outside model extent at row=" + i + ": time" + t
                        + " track=" + track + " posM=" + m + " extent=[" + ex.minM + "," + ex.maxM + "]");
            }

            double pw = p.power(); // (eller din getter)
            if (!Double.isFinite(pw)) throw new IllegalArgumentException("Non-finite power at i=" + i + ": " + pw);
        }
    }

    private static boolean isAnchorNode(Node n) {
        if (n == null) return false;
        if (n.get_internal_id() == 99) return true;
        return  false;
    }

}
