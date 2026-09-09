package org.supply.track;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class DefaultTrackTransformServiceTest {

    @Test
    public void mapsWeRoutePositionAcrossSections() {
        LoadedTrackModel sectionModel =
                new TrackLoader().load(
                        List.of(
                                board("101", "12+400", 12_400, 6_350),
                                board("101", "18+750", 18_750, 0),

                                board("200", "0+000", 0, 6_200),
                                board("200", "6+200", 6_200, 0),

                                board("102", "41+300", 41_300, 6_800),
                                board("102", "48+100", 48_100, 0)
                        ),
                        List.of(),
                        List.of()
                );

        RouteView route =
                new RouteView(
                        "RED-WE",
                        "W-E",
                        List.of(
                                sample(0, "101", 12_400, "U"),
                                sample(6_349, "101", 18_749, "U"),
                                sample(6_350, "200", 0, "U1"),
                                sample(12_549, "200", 6_199, "U1"),
                                sample(12_550, "102", 41_300, "U"),
                                sample(19_350, "102", 48_100, "U")
                        )
                );

        LoadedTrackModel trackModel =
                new LoadedTrackModel(
                        sectionModel.getSectionsById(),
                        sectionModel.getJunctions(),
                        sectionModel.getStations(),
                        Map.of(route.getRouteId(), route)
                );

        TrackTransformService transform =
                new DefaultTrackTransformService(trackModel);

        assertModel(
                transform.pathToModel("RED-WE", 6_349),
                "101",
                "U",
                6_349
        );

        assertModel(
                transform.pathToModel("RED-WE", 6_350),
                "200",
                "U1",
                0
        );

        assertModel(
                transform.pathToModel("RED-WE", 12_549),
                "200",
                "U1",
                6_199
        );

        assertModel(
                transform.pathToModel("RED-WE", 12_550),
                "102",
                "U",
                0
        );
    }

    private static KilometerBoard board(
            String sectionId,
            String kmText,
            int railwayPositionM,
            int lengthToNextM
    ) {
        return new KilometerBoard(
                sectionId,
                kmText,
                railwayPositionM,
                lengthToNextM
        );
    }

    private static PathSample sample(
            double routePositionM,
            String sectionId,
            int railwayPositionM,
            String trackId
    ) {
        return new PathSample(
                routePositionM,
                new RwyCoordinate(
                        sectionId,
                        RwyCoordinate.formatKmShort(railwayPositionM),
                        railwayPositionM,
                        trackId
                )
        );
    }

    private static void assertModel(
            ModelCoordinate actual,
            String expectedSectionId,
            String expectedTrackId,
            int expectedPositionM
    ) {
        assertEquals(expectedSectionId, actual.getSectionId());
        assertEquals(expectedTrackId, actual.getTrackId());
        assertEquals(expectedPositionM, actual.getPositionM());
    }
}