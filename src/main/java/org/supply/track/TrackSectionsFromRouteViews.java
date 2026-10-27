package org.supply.track;

import java.util.*;

/** Builds a section-local distance scale from the physical distances in route views. */
public final class TrackSectionsFromRouteViews {
    // The current model uses integer metres. Allow one metre of rounding / sampling noise.
    private static final double TOLERANCE_M = 1.0;
    private TrackSectionsFromRouteViews() { }

    private record Span(int low, int high, double lengthM) {
        double distance(int start, int end) {
            return lengthM * (end - start) / (high - low);
        }
    }
    private record Run(String route, String section, List<PathSample> samples) { }

    public static Map<String, TrackSection> build(Map<String, RouteView> views) {
        return build(views, List.of());
    }

    public static Map<String, TrackSection> build(Map<String, RouteView> views, List<TrackJunction> junctions) {
        Map<String, List<Span>> spansBySection = new TreeMap<>();
        Map<String, List<Run>> runsBySection = new TreeMap<>();
        // Stable order makes the derived geometry deterministic.
        for (String id : new TreeSet<>(views.keySet())) {
            List<PathSample> samples = views.get(id).getSamples();
            int start = 0;
            while (start < samples.size()) {
                RwyCoordinate first = samples.get(start).getRailwayCoordinate();
                int end = start + 1;
                while (end < samples.size()) {
                    RwyCoordinate next = samples.get(end).getRailwayCoordinate();
                    if (!first.getSectionId().equals(next.getSectionId())
                            || !Objects.equals(first.getTrackId(), next.getTrackId())) break;
                    end++;
                }
                List<PathSample> run = samples.subList(start, end);
                runsBySection.computeIfAbsent(first.getSectionId(), k -> new ArrayList<>())
                        .add(new Run(id, first.getSectionId(), List.copyOf(run)));
                int direction = 0;
                for (int i = 1; i < run.size(); i++) {
                    PathSample a = run.get(i - 1), b = run.get(i);
                    int x = a.getRailwayCoordinate().getPositionM(), y = b.getRailwayCoordinate().getPositionM();
                    double length = b.getPathPositionM() - a.getPathPositionM();
                    if (x == y && length == 0) continue; // Identical duplicated samples.
                    if (x == y || length <= 0) throw bad(id, first.getSectionId(), "inconsistent railway/path positions");
                    int sign = Integer.compare(y, x);
                    if (direction != 0 && sign != direction)
                        throw bad(id, first.getSectionId(), "railway positions change direction within a track interval");
                    direction = sign;
                    spansBySection.computeIfAbsent(first.getSectionId(), k -> new ArrayList<>())
                            .add(new Span(Math.min(x, y), Math.max(x, y), length));
                }
                start = end;
            }
        }
        extendCoveredJunctionEdges(views, junctions, spansBySection);
        Map<String, TrackSection> sections = new LinkedHashMap<>();
        for (var entry : runsBySection.entrySet()) {
            String id = entry.getKey();
            List<Span> spans = spansBySection.getOrDefault(id, List.of());
            if (spans.isEmpty()) throw new IllegalArgumentException(
                    "Cannot derive section " + id + ": at least two positions on the same track are needed");
            TreeSet<Integer> boundaries = new TreeSet<>();
            spans.forEach(s -> { boundaries.add(s.low); boundaries.add(s.high); });
            List<Integer> points = new ArrayList<>(boundaries);
            List<RouteSegment> segments = new ArrayList<>();
            double total = 0;
            for (int i = 0; i + 1 < points.size(); i++) {
                int low = points.get(i), high = points.get(i + 1);
                Span selected = null;
                for (Span s : spans) if (s.low <= low && s.high >= high) { selected = s; break; }
                if (selected == null) throw new IllegalArgumentException("Cannot derive section " + id
                        + ": no route data covers railway positions " + low + " to " + high);
                double length = selected.distance(low, high);
                int startModel = (int) Math.round(total);
                total += length;
                if (!Double.isFinite(total) || total > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Derived section length exceeds supported range: " + id);
                int endModel = (int) Math.round(total);
                if (endModel <= startModel) throw new IllegalArgumentException("Cannot represent derived span in integer metres: " + id + " " + low + " to " + high);
                segments.add(new RouteSegment(i, coordinate(id, low), coordinate(id, high), startModel, endModel - startModel));
            }
            TrackSection section = new TrackSection(id, segments);
            // Compare cumulative distances, not only individual spans, so small discrepancies cannot accumulate silently.
            TrackSectionMapper mapper = new TrackSectionMapper();
            for (Run run : entry.getValue()) {
                if (run.samples.size() < 2) continue;
                PathSample first = run.samples.get(0);
                int firstModel = mapper.toModel(section, first.getRailwayCoordinate()).getPositionM();
                for (PathSample sample : run.samples) {
                    int model = mapper.toModel(section, sample.getRailwayCoordinate()).getPositionM();
                    double expected = sample.getPathPositionM() - first.getPathPositionM();
                    if (Math.abs(Math.abs(model - firstModel) - expected) > TOLERANCE_M + 1e-9)
                        throw bad(run.route, id, "incompatible distances between route views or tracks (more than 1 m)");
                }
                // Check all shared breakpoints, including points interpolated inside a sparse route view.
                // Equal end-to-end lengths can otherwise hide different intermediate distances.
                for (int i = 1; i < run.samples.size(); i++) {
                    PathSample a = run.samples.get(i - 1), b = run.samples.get(i);
                    int x = a.getRailwayCoordinate().getPositionM(), y = b.getRailwayCoordinate().getPositionM();
                    if (x == y) continue;
                    for (int position : points) {
                        if (position <= Math.min(x, y) || position >= Math.max(x, y)) continue;
                        double fraction = (double) (position - x) / (y - x);
                        double expected = a.getPathPositionM() - first.getPathPositionM()
                                + fraction * (b.getPathPositionM() - a.getPathPositionM());
                        int model = mapper.toModel(section, coordinate(id, position)).getPositionM();
                        if (Math.abs(Math.abs(model - firstModel) - expected) > TOLERANCE_M + 1e-9)
                            throw bad(run.route, id, "incompatible distances between route views or tracks (more than 1 m)");
                    }
                }
            }
            sections.put(id, section);
        }
        return Collections.unmodifiableMap(sections);
    }

    // A route can record the two sides of a section boundary one metre apart.
    // Extend only a matched junction coordinate, and at most one railway metre.
    private static void extendCoveredJunctionEdges(Map<String, RouteView> views, List<TrackJunction> junctions,
                                                     Map<String, List<Span>> spansBySection) {
        for (RouteView view : views.values()) {
            List<PathSample> samples = view.getSamples();
            for (int i = 1; i < samples.size(); i++) {
                PathSample a = samples.get(i-1), b = samples.get(i);
                RwyCoordinate x = a.getRailwayCoordinate(), y = b.getRailwayCoordinate();
                if (x.getSectionId().equals(y.getSectionId()) || b.getPathPositionM()-a.getPathPositionM() > 1.0) continue;
                for (TrackJunction junction : junctions) {
                    RwyCoordinate from = junction.getFrom(), to = junction.getTo();
                    if (matchesBoundary(x, from) && matchesBoundary(y, to)) {
                        extendEdge(spansBySection, from); extendEdge(spansBySection, to);
                    } else if (matchesBoundary(x, to) && matchesBoundary(y, from)) {
                        extendEdge(spansBySection, to); extendEdge(spansBySection, from);
                    }
                }
            }
        }
    }
    private static boolean matchesBoundary(RwyCoordinate sample, RwyCoordinate junction) {
        return sample.getSectionId().equals(junction.getSectionId())
                && Objects.equals(sample.getTrackId(), junction.getTrackId())
                && Math.abs((long) sample.getPositionM()-junction.getPositionM()) <= 1;
    }
    private static void extendEdge(Map<String,List<Span>> spansBySection, RwyCoordinate junction) {
        List<Span> spans = spansBySection.get(junction.getSectionId());
        if (spans == null || spans.isEmpty()) return;
        int pos = junction.getPositionM();
        int low = spans.stream().mapToInt(Span::low).min().orElseThrow();
        int high = spans.stream().mapToInt(Span::high).max().orElseThrow();
        if (pos >= low && pos <= high) return;
        int edge = pos < low ? low : high;
        if (Math.abs((long) pos-edge) != 1) return;
        Span adjacent = spans.stream().filter(s -> pos < low ? s.low == edge : s.high == edge).findFirst().orElseThrow();
        double length = adjacent.lengthM / (adjacent.high-adjacent.low);
        spans.add(new Span(Math.min(pos, edge), Math.max(pos, edge), length));
    }
    private static RwyCoordinate coordinate(String section, int position) {
        return new RwyCoordinate(section, RwyCoordinate.formatKmShort(position), position, null);
    }
    private static IllegalArgumentException bad(String route, String section, String detail) {
        return new IllegalArgumentException("Cannot derive section " + section + " from route " + route + ": " + detail);
    }
}
