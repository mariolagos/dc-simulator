package org.supply.track;

import java.util.*;

/** Resolves a unique section chain using the track model's explicit junctions. */
public final class TrackLinePath {
    private final LoadedTrackModel track;

    public TrackLinePath(LoadedTrackModel track) {
        this.track = Objects.requireNonNull(track, "track");
    }

    public List<ModelCoordinate> between(ModelCoordinate from, ModelCoordinate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.getSectionId().equals(to.getSectionId())) return List.of(from, to);
        if (!compatible(from.getTrackId(), to.getTrackId())) {
            throw new IllegalArgumentException("Line endpoints have incompatible tracks");
        }
        List<List<ModelCoordinate>> paths = new ArrayList<>();
        search(from, to, new HashSet<>(), new ArrayList<>(List.of(from)), paths);
        if (paths.isEmpty()) throw new IllegalArgumentException(
                "No track junction path between sections " + from.getSectionId() + " and " + to.getSectionId());
        if (paths.size() > 1) throw new IllegalArgumentException(
                "Ambiguous track junction path between sections " + from.getSectionId() + " and " + to.getSectionId()
                        + "; define electrical line endpoints at the intermediate junctions");
        return List.copyOf(paths.get(0));
    }

    private void search(ModelCoordinate current, ModelCoordinate target, Set<String> visited,
                        List<ModelCoordinate> path, List<List<ModelCoordinate>> paths) {
        if (paths.size() > 1) return;
        if (current.getSectionId().equals(target.getSectionId())) {
            if (compatible(current.getTrackId(), target.getTrackId())) {
                List<ModelCoordinate> result = new ArrayList<>(path);
                result.add(target);
                paths.add(result);
            }
            return;
        }
        visited.add(current.getSectionId());
        for (TrackJunction junction : track.getJunctions()) {
            RwyCoordinate exit = junction.getFrom(), entry = junction.getTo();
            if (!exit.getSectionId().equals(current.getSectionId())) {
                RwyCoordinate swap = exit; exit = entry; entry = swap;
            }
            if (!exit.getSectionId().equals(current.getSectionId())
                    || visited.contains(entry.getSectionId())
                    || !compatible(current.getTrackId(), exit.getTrackId())
                    || !compatible(current.getTrackId(), entry.getTrackId())
                    || !compatible(target.getTrackId(), entry.getTrackId())) continue;
            String trackId = current.getTrackId();
            if (trackId == null || trackId.isBlank()) trackId = target.getTrackId();
            ModelCoordinate a = model(exit, trackId), b = model(entry, trackId);
            path.add(a); path.add(b);
            search(b, target, visited, path, paths);
            path.remove(path.size() - 1); path.remove(path.size() - 1);
        }
        visited.remove(current.getSectionId());
    }

    private ModelCoordinate model(RwyCoordinate coordinate, String fallbackTrack) {
        TrackSection section = track.getSectionsById().get(coordinate.getSectionId());
        if (section == null) throw new IllegalArgumentException("Unknown junction section: " + coordinate.getSectionId());
        String trackId = coordinate.getTrackId();
        if (trackId == null || trackId.isBlank()) trackId = fallbackTrack;
        return new TrackSectionMapper().toModel(section, new RwyCoordinate(
                coordinate.getSectionId(), coordinate.getPositionText(), coordinate.getPositionM(), trackId));
    }

    private static boolean compatible(String a, String b) {
        return a == null || a.isBlank() || b == null || b.isBlank() || a.equals(b);
    }
}
