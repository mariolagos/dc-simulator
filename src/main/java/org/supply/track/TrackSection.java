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
}