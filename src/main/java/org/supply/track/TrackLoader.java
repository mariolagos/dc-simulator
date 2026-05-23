package org.supply.track;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loader for simplified track configuration.
 *
 * Important:
 * - kilometer board order is preserved
 * - no sorting is performed
 * - sections are built from consecutive kilometer boards
 */
public final class TrackLoader {

    public LoadedTrackModel load(List<KilometerBoard> kilometerBoards,
                                 List<TrackJunction> junctions,
                                 List<Station> stations) {

        Objects.requireNonNull(kilometerBoards, "kilometerBoards");
        Objects.requireNonNull(junctions, "junctions");
        Objects.requireNonNull(stations, "stations");

        Map<String, List<KilometerBoard>> boardsBySection = groupBoardsBySectionPreservingOrder(kilometerBoards);
        Map<String, TrackSection> sectionsById = buildSections(boardsBySection);

        return new LoadedTrackModel(sectionsById, junctions, stations);
    }

    private Map<String, List<KilometerBoard>> groupBoardsBySectionPreservingOrder(List<KilometerBoard> kilometerBoards) {
        Map<String, List<KilometerBoard>> result = new LinkedHashMap<>();

        for (KilometerBoard board : kilometerBoards) {
            result.computeIfAbsent(board.getSectionId(), ignored -> new ArrayList<>())
                    .add(board);
        }

        return result;
    }

    private Map<String, TrackSection> buildSections(Map<String, List<KilometerBoard>> boardsBySection) {
        Map<String, TrackSection> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<KilometerBoard>> entry : boardsBySection.entrySet()) {
            String sectionId = entry.getKey();
            List<KilometerBoard> boards = entry.getValue();

            if (boards.size() < 2) {
                throw new IllegalArgumentException(
                        "Section '" + sectionId + "' must contain at least two kilometer boards");
            }

            List<RouteSegment> segments = new ArrayList<>();
            int startModelM = 0;

            for (int i = 0; i < boards.size() - 1; i++) {
                KilometerBoard from = boards.get(i);
                KilometerBoard to = boards.get(i + 1);

                RwyCoordinate startRwy = from.toRwyCoordinate();
                RwyCoordinate endRwy = to.toRwyCoordinate();

                int lengthM = from.getLengthToNextM();

                RouteSegment segment = new RouteSegment(
                        i,
                        startRwy,
                        endRwy,
                        startModelM,
                        lengthM
                );

                segments.add(segment);
                startModelM += lengthM;
            }

            result.put(sectionId, new TrackSection(sectionId, segments));
        }

        return result;
    }
}