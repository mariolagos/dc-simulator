package org.supply.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class DefaultTrackTransformServiceTest {

    @Test
    public void shouldComputeDistanceOnSection() {
        TrackLoader loader = new TrackLoader();

        List<KilometerBoard> boards = List.of(
                new KilometerBoard("S1", "0+200", 200, 100),
                new KilometerBoard("S1", "0+100", 100, 50),
                new KilometerBoard("S1", "0+050", 50, 0)
        );

        LoadedTrackModel model = loader.load(boards, List.of(), List.of());

        DefaultTrackTransformService service = new DefaultTrackTransformService(model);

        RwyCoordinate a = new RwyCoordinate("S1", "0+180", 180, null);
        RwyCoordinate b = new RwyCoordinate("S1", "0+080", 80, null);

        double distance = service.distanceOnRoute("S1", a, b);

        assertEquals(100.0, distance, 0.0001);
    }
}