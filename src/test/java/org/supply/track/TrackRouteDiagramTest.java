package org.supply.track;

import org.junit.Test;
import java.util.*;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import static org.junit.Assert.*;

public class TrackRouteDiagramTest {
    private static RwyCoordinate coordinate(int m,String track) { return new RwyCoordinate("1",RwyCoordinate.formatKmShort(m),m,track); }
    @Test public void mapsReverseRouteAndDoesNotMixTracks() throws Exception {
        RouteView route=new RouteView("A&B",null,List.of(new PathSample(0,coordinate(2000,"U")),new PathSample(1000,coordinate(1000,"U"))));
        assertEquals(List.of(500.0),TrackRouteDiagram.positions(route,coordinate(1500,"U")));
        assertTrue(TrackRouteDiagram.positions(route,coordinate(1500,"D")).isEmpty());
        LoadedTrackModel model=new LoadedTrackModel(Map.of(),List.of(),List.of(new Station("S<&",coordinate(1500,"U"))),Map.of("A&B",route));
        String svg=TrackRouteDiagram.svg(model,route);
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        assertTrue(svg.contains("500.0 m"));assertTrue(svg.contains("S&lt;&amp;"));
    }
    @Test public void doesNotInterpolateAcrossSectionOrTrackTransitions() {
        RouteView route=new RouteView("change",null,List.of(new PathSample(0,coordinate(1000,"U")),new PathSample(1,coordinate(2000,"D"))));
        assertTrue(TrackRouteDiagram.positions(route,coordinate(1500,"U")).isEmpty());
    }
    @Test public void preservesDistancesAndDrawsConfiguredBoards() throws Exception {
        RouteView route=new RouteView("U",null,List.of(new PathSample(0,coordinate(0,"U")),new PathSample(2000,coordinate(2000,"U"))));
        TrackSection section=new TrackSection("1",List.of(new RouteSegment(0,coordinate(0,null),coordinate(1000,null),0,1000),new RouteSegment(1,coordinate(1000,null),coordinate(2000,null),1000,1000)));
        LoadedTrackModel model=new LoadedTrackModel(Map.of("1",section),List.of(),List.of(new Station("A",coordinate(500,"U")),new Station("B",coordinate(1500,"U"))));
        var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(TrackRouteDiagram.svg(model,route))));
        var circles=document.getElementsByTagName("circle");
        double a=Double.parseDouble(circles.item(0).getAttributes().getNamedItem("cy").getNodeValue());
        double b=Double.parseDouble(circles.item(1).getAttributes().getNamedItem("cy").getNodeValue());
        assertEquals(60,b-a,1e-9);
        assertTrue(document.getDocumentElement().getTextContent().contains("1 1+0"));
        assertTrue(document.getDocumentElement().getTextContent().contains("1000.0 m"));
    }
    @Test public void drawsVerticalBandWithDistinctObjects() throws Exception {
        RouteView route=new RouteView("U",null,List.of(new PathSample(0,coordinate(0,"U")),new PathSample(2000,coordinate(2000,"U"))));
        LoadedTrackModel model=new LoadedTrackModel(Map.of(),List.of(new TrackJunction(coordinate(1000,"U"),coordinate(1000,"D"))),List.of(new Station("Station",coordinate(500,"U"))));
        String svg=TrackRouteDiagram.bandSvg(model,route,List.of(new TrackRouteDiagram.ObjectPoint("substation","SS",coordinate(1500,"U"))));
        var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        assertTrue(svg.contains("Station"));assertTrue(svg.contains("SS"));assertTrue(svg.contains("Connection:"));
        assertTrue(svg.contains("Route start"));assertTrue(svg.contains("Route end"));
        var line=document.getElementsByTagName("line").item(0).getAttributes();
        assertEquals(line.getNamedItem("x1").getNodeValue(),line.getNamedItem("x2").getNodeValue());
        assertEquals(1,document.getElementsByTagName("circle").getLength());
        assertEquals(2,document.getElementsByTagName("rect").getLength());
    }
}
