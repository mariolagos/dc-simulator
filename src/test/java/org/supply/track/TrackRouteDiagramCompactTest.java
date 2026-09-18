package org.supply.track;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TrackRouteDiagramCompactTest {
    private static RwyCoordinate c(String section,int m) {
        return new RwyCoordinate(section,(m/1000)+"+"+(m%1000),m,null);
    }
    @Test public void groupsCoincidentReferencesWithoutDuplicateConnections() {
        RouteView route=new RouteView("F-M",null,List.of(new PathSample(0,c("23",8070)),
                new PathSample(4493.999,c("23",3576)),new PathSample(4494,c("21",3576)),new PathSample(8070,c("21",0))));
        LoadedTrackModel model=new LoadedTrackModel(Map.of(),
                List.of(new TrackJunction(c("23",3576),c("21",3576))),List.of());
        String svg=TrackRouteDiagram.svg(model,route);
        assertTrue(svg.contains("Junction: 23 3+576 / 21 3+576"));
        assertFalse(svg.contains("Section/track exit"));assertFalse(svg.contains("Section/track entry"));
        assertFalse(svg.contains("Connection:"));assertFalse(svg.contains("| route"));
        assertTrue(svg.contains("23 8+070  0.0 m"));assertTrue(svg.contains("width=\"1100\""));
    }
    @Test public void retainsTransitionsSeparatedByRealDistance() {
        RouteView route=new RouteView("gap",null,List.of(new PathSample(0,c("23",3576)),new PathSample(10,c("21",3576))));
        String svg=TrackRouteDiagram.svg(new LoadedTrackModel(Map.of(),List.of(),List.of()),route);
        assertTrue(svg.contains("Section/track exit"));assertTrue(svg.contains("Section/track entry"));
    }
}
