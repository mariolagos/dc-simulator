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

public class CombinedRouteSchematicTest {
    @Test public void rendersFourStraightLanesAndStationElectricalValues() throws Exception {
        List<CalculationNode> nodes=new ArrayList<>();List<CalculationBranch> branches=new ArrayList<>();
        List<Route> routes=new ArrayList<>();
        for(String side:List.of("U","D")) {
            for(String kind:List.of("F","R")) {
                for(String location:List.of("START","S","END")) {
                    String id=kind+"_"+side+"_"+location;
                    nodes.add(new CalculationNode(id,id,"1",side,location.equals("START")?0:location.equals("END")?2000:1000,CalculationNodeType.GRID_NODE));
                }
                for(int i=1;i<=2;i++) {
                    String id=kind+"_"+side+"_"+i;
                    branches.add(new CalculationBranch(id,id,kind+"_"+side+"_"+(i==1?"START":"S"),kind+"_"+side+"_"+(i==1?"S":"END"),new Real(0.1)));
                }
            }
            routes.add(new Route(side,side.equals("U")?List.of("F_U_1","F_U_2"):List.of("F_D_2","F_D_1"),side.equals("U")?List.of("R_U_1","R_U_2"):List.of("R_D_2","R_D_1")));
        }
        for(String kind:List.of("F","R")) { branches.add(new CalculationBranch("internal_"+kind,"internal_S",kind+"_U_S",kind+"_D_S",new Real(1e-9))); }
        List<ElectricalElement> elements=new ArrayList<>(branches);
        elements.add(new DiodeSubstationElement("SS","F_U_S","R_U_S",new Real(3120),new Real(0.08),true));
        Map<String,Double> resistance=new HashMap<>();for(var b:branches) { resistance.put(b.sourceId(),0.000015); }
        String svg=RouteSchematic.combinedSvg(new CalculationNetwork(nodes,branches,List.of(),elements),routes,resistance);
        assertTrue(svg.contains("EMF=3120"));assertTrue(svg.contains("Rint=0.080"));
        assertTrue(svg.contains("feeding=4, return=4"));assertTrue(svg.contains("ohm/m"));
        var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        var lines=document.getElementsByTagName("line");Set<String> levels=new HashSet<>();
        for(int i=0;i<lines.getLength();i++) {
            var a=lines.item(i).getAttributes();String y=a.getNamedItem("y1").getNodeValue();
            assertEquals(y,a.getNamedItem("y2").getNodeValue());levels.add(y);
            assertEquals(y.equals("160")||y.equals("400")?"#b42318":"#175cd3",a.getNamedItem("stroke").getNodeValue());
        }
        assertEquals(Set.of("160","240","400","480"),levels);
    }
}
