package org.supply.track;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TrackRouteJunctionPresentationTest {
    private static RwyCoordinate c(String section,int p,String track) {return new RwyCoordinate(section,RwyCoordinate.formatKmShort(p),p,track);}
    private static int count(String text,String token) {return (text.length()-text.replace(token,"").length())/token.length();}
    @Test public void consolidatesSectionBoundaryAndBranchConnection() {
        var a=c("1",1000,"N");var b=c("2",0,"N");var off=c("3",0,"N");
        var route=new RouteView("R",null,List.of(new PathSample(0,c("1",0,"N")),new PathSample(1000,a),new PathSample(1000.001,b),new PathSample(2000,c("2",1000,"N"))));
        var model=new LoadedTrackModel(Map.of(),List.of(new TrackJunction(a,b),new TrackJunction(off,b)),List.of(new Station("Same",a),new Station("Same",b)));
        String svg=TrackRouteDiagram.svg(model,route);
        assertEquals(1,count(svg,"class=\"junction-marker\""));
        assertEquals(1,count(svg,">Section change<"));assertEquals(1,count(svg,">Same<"));
        assertTrue(svg.contains("3 0+000 N"));assertFalse(svg.contains("track-change-arrow"));
    }
    @Test public void trackChangeHasArrowAndRepeatedVisitsRemain() {
        var a=c("1",1000,"N");var b=c("1",1000,"S");
        var route=new RouteView("R",null,List.of(new PathSample(0,c("1",0,"N")),new PathSample(1000,a),new PathSample(1000.001,b),new PathSample(2000,c("1",0,"S"))));
        var model=new LoadedTrackModel(Map.of(),List.of(new TrackJunction(a,b)),List.of(new Station("Visit",c("1",500,null))));
        String svg=TrackRouteDiagram.svg(model,route);
        assertEquals(1,count(svg,"class=\"junction-marker\""));assertTrue(svg.contains("track-change-arrow"));
        assertEquals(2,count(svg,">Visit<"));
    }
}
