package org.supply.track;

import java.util.List;
import java.util.Objects;

/**
 * Internal representation of one section with ordered segments.
 */
public final class TrackSection {

    private final String sectionId;
    private final List<RouteSegment> segments;

    public TrackSection(String sectionId, List<RouteSegment> segments) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
    }

    public String getSectionId() {
        return sectionId;
    }

    public List<RouteSegment> getSegments() {
        return segments;
    }
    /** Select one distance scale. Explicit board sections have unnamed segments. */
    public List<RouteSegment> getSegments(String trackId) {
        java.util.Set<String> tracks = new java.util.HashSet<>();
        for (RouteSegment segment : segments) tracks.add(segment.getStartRwy().getTrackId());
        if (tracks.size() == 1 && tracks.contains(null)) return segments;
        if (trackId == null || trackId.isBlank()) {
            if (tracks.size() == 1) return segments;
            throw new IllegalArgumentException("Track required for section " + sectionId
                    + "; available tracks: " + new java.util.TreeSet<>(tracks));
        }
        List<RouteSegment> selected = segments.stream()
                .filter(segment -> trackId.equals(segment.getStartRwy().getTrackId())).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("No geometry for section " + sectionId
                + " track " + trackId);
        return selected;
    }
}