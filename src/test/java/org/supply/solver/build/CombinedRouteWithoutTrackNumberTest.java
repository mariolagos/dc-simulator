package org.supply.solver.build;
import org.junit.Test;
import org.supply.domain.Route;
import org.supply.math.Real;
import org.supply.solver.model.*;
import java.util.*;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import static org.junit.Assert.*;
public class CombinedRouteWithoutTrackNumberTest {
    @Test public void rendersTwoRoutesWithMissingTrackNumbers() throws Exception {
        List<CalculationNode> nodes=new ArrayList<>();
        List<CalculationBranch> branches=new ArrayList<>();
        for(String lane:List.of("N","S")) for(String kind:List.of("F","R")) {
            String a=kind+"_0_"+lane,z=kind+"_1_"+lane,id=kind+"_"+lane;
            nodes.add(new CalculationNode(a,a,"23",null,0,CalculationNodeType.GRID_NODE));
            nodes.add(new CalculationNode(z,z,"23",null,1000,CalculationNodeType.GRID_NODE));
            branches.add(new CalculationBranch(id,id,a,z,new Real(0.015)));
        }
        CalculationNetwork network=new CalculationNetwork(nodes,branches,List.of(),new ArrayList<>(branches));
        List<Route> routes=List.of(new Route("M-F",List.of("F_S"),List.of("R_S")),
                new Route("F-M",List.of("F_N"),List.of("R_N")));
        String svg=RouteSchematic.combinedSvg(network,routes,Map.of());
        assertTrue(svg.contains("Route F-M + M-F"));
        assertTrue(svg.contains("feeding=2, return=2"));
        var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        var lines=document.getElementsByTagName("line");Set<String> levels=new HashSet<>();
        for(int i=0;i<lines.getLength();i++) levels.add(lines.item(i).getAttributes().getNamedItem("y1").getNodeValue());
        assertEquals(Set.of("160","240","400","480"),levels);
        assertEquals(svg,RouteSchematic.combinedSvg(network,List.of(routes.get(1),routes.get(0)),Map.of()));
    }
}
