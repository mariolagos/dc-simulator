package org.supply.solver.build;
import org.junit.Test;
import org.supply.domain.Route;
import org.supply.math.Real;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.util.*;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import static org.junit.Assert.*;

public class RouteSchematicJunctionTest {
    private static CalculationNode node(String id,String section,double metre) {
        return new CalculationNode(id,id,section,null,metre,CalculationNodeType.GRID_NODE);
    }
    private static CalculationBranch branch(String id,String from,String to,double r) {
        return new CalculationBranch(id,id,from,to,new Real(r));
    }
    @Test public void groupsOnlyExplicitNearIdealConnectorsAndRetainsSourceLabels() throws Exception {
        List<CalculationNode> nodes=List.of(node("start","23",4324),node("b23","23",0),node("b21","21",3576),node("end","21",2200));
        List<CalculationBranch> branches=List.of(branch("first","start","b23",0.1),branch("bridge","b23","b21",1e-15),branch("last","b21","end",0.1));
        CalculationNetwork network=new CalculationNetwork(nodes,branches,List.of(),new ArrayList<>(branches));
        Route route=new Route("F-M",List.of("first","bridge","last"),List.of());
        RwyCoordinate b23=new RwyCoordinate("23","3+576",3576,null),b21=new RwyCoordinate("21","3+576",3576,null);
        Map<String,RwyCoordinate> positions=Map.of("start",new RwyCoordinate("23","7+900",7900,null),"b23",b23,"b21",b21,"end",new RwyCoordinate("21","2+200",2200,null));
        Map<String,Double> resistance=Map.of("first",0.000015,"bridge",1e-15,"last",0.000015);
        var context=new RouteSchematic.Context(positions,List.of(new TrackJunction(b23,b21)));
        String svg=RouteSchematic.svg(network,route,resistance,context);
        var doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        assertEquals(2,doc.getElementsByTagName("line").getLength());
        assertEquals(3,doc.getElementsByTagName("circle").getLength());
        assertTrue(svg.contains("23 7+900"));assertTrue(svg.contains(">4324 m<"));
        assertTrue(svg.contains("23 3+576"));assertTrue(svg.contains("21 3+576"));
        assertTrue(svg.contains("junction connectors omitted: 1"));
        assertEquals(4,network.nodes().size());assertEquals(3,network.branches().size());
        String unconfigured=RouteSchematic.svg(network,route,resistance,new RouteSchematic.Context(positions,List.of()));
        var ungrouped=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(unconfigured)));
        assertEquals(3,ungrouped.getElementsByTagName("line").getLength());
        String nonIdeal=RouteSchematic.svg(network,route,Map.of("bridge",0.000015),context);
        var real=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(nonIdeal)));
        assertEquals(3,real.getElementsByTagName("line").getLength());
    }
}
