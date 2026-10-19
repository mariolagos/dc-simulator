package org.supply.solver.build;
import org.junit.Test;
import org.supply.domain.Route;
import org.supply.math.Real;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.util.*;
import static org.junit.Assert.*;
public class RouteJunctionPresentationTest {
    private static CalculationNode n(String id,String section,String track,double p) {return new CalculationNode(id,id,section,track,p,CalculationNodeType.GRID_NODE);}
    private static CalculationBranch b(String id,String a,String z,boolean join) {return new CalculationBranch(id,id,a,z,new Real(join?1e-9:.1),join);}
    private static int count(String s,String q) {return (s.length()-s.replace(q,"").length())/q.length();}
    @Test public void oneJunctionDescriptionForFeedingAndReturn() {
        var nodes=List.of(n("f0","1","N",0),n("fa","1","N",1000),n("fb","2","N",0),n("f1","2","N",1000),n("r0","1","N",0),n("ra","1","N",1000),n("rb","2","N",0),n("r1","2","N",1000));
        var branches=List.of(b("f0","f0","fa",false),b("fj","fa","fb",true),b("f1","fb","f1",false),b("r0","r0","ra",false),b("rj","ra","rb",true),b("r1","rb","r1",false));
        var network=new CalculationNetwork(nodes,branches,List.of(),new ArrayList<>(branches));
        var a=new RwyCoordinate("1","1+000",1000,"N");var z=new RwyCoordinate("2","0+000",0,"N");
        String svg=RouteSchematic.svg(network,new Route("R",List.of("f0","fj","f1"),List.of("r0","rj","r1")),Map.of(),new RouteSchematic.Context(Map.of("fa",a,"ra",a,"fb",z,"rb",z),List.of()));
        assertEquals(1,count(svg,"class=\"physical-junction\""));assertEquals(1,count(svg,">Section change<"));
        assertEquals(1,count(svg,">1 1+000 N<"));assertFalse(svg.contains("track-change-arrow"));
        assertEquals(8,network.nodes().size());
    }
    @Test public void coLocatedTrackChangeHasArrow() {
        var nodes=List.of(n("a","1","N",0),n("b","1","N",1000),n("c","1","S",1000),n("d","1","S",2000));
        var branches=List.of(b("a","a","b",false),b("j","b","c",true),b("z","c","d",false));
        var network=new CalculationNetwork(nodes,branches,List.of(),new ArrayList<>(branches));
        String svg=RouteSchematic.svg(network,new Route("R",List.of("a","j","z"),List.of()),Map.of());
        assertTrue(svg.contains("track-change-arrow"));assertEquals(1,count(svg,">Track change<"));
    }
}
