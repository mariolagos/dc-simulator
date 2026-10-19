package org.supply.track;
import org.junit.Test;
import java.util.*;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import static org.junit.Assert.*;
public class TrackRouteOneMetreJunctionTest {
    private static RwyCoordinate c(String section,int p) {return new RwyCoordinate(section,RwyCoordinate.formatKmShort(p),p,"N");}
    private static int count(String s,String q) {return (s.length()-s.replace(q,"").length())/q.length();}
    @Test public void groupsOneMetreBoundaryWithBranchAndKeepsDetailsInTooltip() throws Exception {
        var a=c("201",13999);var b=c("200",0);var off=new RwyCoordinate("101","18+750",18750,null);
        var route=new RouteView("NwSe",null,List.of(new PathSample(0,c("201",7850)),new PathSample(6149,a),new PathSample(6150,b),new PathSample(7000,c("200",850))));
        var model=new LoadedTrackModel(Map.of(),List.of(new TrackJunction(new RwyCoordinate("201","14+000",14000,null),new RwyCoordinate("200","0+000",0,null)),new TrackJunction(off,new RwyCoordinate("200","0+000",0,null))),List.of());
        String svg=TrackRouteDiagram.svg(model,route);
        assertEquals(1,count(svg,"class=\"junction-event\""));assertEquals(1,count(svg,"class=\"junction-marker\""));
        assertFalse(svg.contains("Section/track exit"));assertFalse(svg.contains("Section/track entry"));
        assertTrue(svg.contains("201 N → 200 N (101)"));
        var doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        assertTrue(doc.getElementsByTagName("title").item(0).getTextContent().contains("201 13+999 N"));
        assertTrue(doc.getElementsByTagName("title").item(0).getTextContent().contains("101 18+750"));
    }
    @Test public void preservesLargerGaps() {
        var route=new RouteView("gap",null,List.of(new PathSample(0,c("201",13999)),new PathSample(2,c("200",0))));
        String svg=TrackRouteDiagram.svg(new LoadedTrackModel(Map.of(),List.of(),List.of()),route);
        assertTrue(svg.contains("Section/track exit"));assertTrue(svg.contains("Section/track entry"));
    }
    public static void main(String[] args)throws Exception {
        var route=new RouteView("NwSe",null,List.of(new PathSample(0,c("201",7850)),new PathSample(6149,c("201",13999)),new PathSample(6150,c("200",0)),new PathSample(7000,c("200",850))));
        var model=new LoadedTrackModel(Map.of(),List.of(new TrackJunction(c("201",14000),c("200",0)),new TrackJunction(c("101",18750),c("200",0))),List.of(new Station("NW-Mid",c("201",10500)),new Station("CW",c("200",100))));
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),TrackRouteDiagram.svg(model,route));
    }
}
