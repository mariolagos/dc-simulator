package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.Route;
import org.supply.math.Real;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class RouteTopologyOutputTest {
    private static CalculationNode node(String id,String section,double metre) {
        return new CalculationNode(id,id,section,"N",metre,CalculationNodeType.GRID_NODE);
    }
    private static CalculationBranch branch(String id,String from,String to,double resistance) {
        return new CalculationBranch(id,id,from,to,new Real(resistance));
    }
    @Test public void writesOneSvgPerRouteAndRemovesObsoleteCombinedOutput() throws Exception {
        var branches=List.of(branch("feed","F0","F1",0.1),branch("return","R0","R1",0.1));
        var network=new CalculationNetwork(List.of(node("F0","1",0),node("F1","1",1000),
                node("R0","1",0),node("R1","1",1000)),branches,List.of(),new ArrayList<>(branches));
        var directory=Files.createTempDirectory("route-output-");
        try {
            Files.writeString(directory.resolve("routes_combined.svg"),"obsolete");
            List<String> warnings=new ArrayList<>();
            ElectricalTopologyGraph.write(network,List.of(new Route("WE",List.of("feed"),List.of("return")),
                    new Route("EW",List.of("feed"),List.of("return"))),null,directory,new StringBuilder(),warnings);
            assertTrue(warnings.toString(),warnings.isEmpty());
            assertFalse(Files.exists(directory.resolve("routes_combined.svg")));
            assertTrue(Files.readString(directory.resolve("route_001_EW.svg")).contains("Route EW | selected route"));
            assertTrue(Files.readString(directory.resolve("route_002_WE.svg")).contains("Route WE | selected route"));
        } finally {
            try(var files=Files.list(directory)) { for(var file:files.toList()) Files.delete(file); }
            Files.delete(directory);
        }
    }
    @Test public void labelsReturnJunctionOnceWithBothRailwayCoordinates() {
        var a=new RwyCoordinate("101","2+000",2000,"N");
        var b=new RwyCoordinate("102","0+000",0,"N");
        var branches=List.of(branch("r1","start","j1",0.1),branch("bridge","j1","j2",1e-15),
                branch("r2","j2","end",0.1));
        var network=new CalculationNetwork(List.of(node("start","101",1000),node("j1","101",2000),
                node("j2","102",0),node("end","102",1000)),branches,List.of(),new ArrayList<>(branches));
        var context=new RouteSchematic.Context(Map.of("j1",a,"j2",b),List.of(new TrackJunction(a,b)));
        String svg=RouteSchematic.svg(network,new Route("AD",List.of(),List.of("r1","bridge","r2")),
                Map.of("bridge",1e-15),context);
        assertEquals(1,svg.split(">Junction<",-1).length-1);
        assertTrue(svg.contains("y=\"370\" font-size=\"12\" fill=\"#175cd3\" text-anchor=\"middle\">101 2+000 N<"));
        assertTrue(svg.contains("y=\"388\" font-size=\"12\" fill=\"#175cd3\" text-anchor=\"middle\">102 0+000 N<"));
        assertEquals(4,network.nodes().size());
        assertEquals(3,network.branches().size());
    }
}
