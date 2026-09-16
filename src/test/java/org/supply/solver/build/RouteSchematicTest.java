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

public class RouteSchematicTest {
    @Test public void drawsBothStraightRailsWithConfiguredOhmPerMeter() throws Exception { check(false); }
    @Test public void reverseRouteKeepsTerminalIdentityAndAllReturns() throws Exception { check(true); }
    private void check(boolean reverse) throws Exception {
        List<CalculationNode> nodes=new ArrayList<>();
        for(String id:List.of("F_START","R_START","F_U_S_LEFT","F_U_S_RIGHT","F_D_S_LEFT","F_D_S_RIGHT","R_U_S","R_D_S","F_END","R_END")) {
            double pos=id.endsWith("START")?0:id.endsWith("END")?2000:1000;
            nodes.add(new CalculationNode(id,id,"1","U",pos,CalculationNodeType.GRID_NODE));
        }
        List<CalculationBranch> branches=List.of(
                branch("f1","feed_1","F_START","F_U_S_LEFT"),branch("f2","feed_2","F_U_S_RIGHT","F_END"),
                branch("r1","return_1","R_START","R_U_S"),branch("r2","return_2","R_U_S","R_END"),
                branch("i1","internal_S","F_U_S_LEFT","F_U_S_RIGHT"),branch("i2","internal_S","F_U_S_LEFT","F_D_S_LEFT"),
                branch("i3","internal_S","F_U_S_LEFT","F_D_S_RIGHT"),branch("i4","internal_S","R_U_S","R_D_S"));
        List<ElectricalElement> elements=new ArrayList<>(branches);
        elements.add(new DiodeSubstationElement("S","F_U_S_LEFT","R_U_S",new Real(3000),new Real(0.08),true));
        Route route=new Route("RED",reverse?List.of("feed_2","feed_1"):List.of("feed_1","feed_2"),
                reverse?List.of("return_2","return_1"):List.of("return_1","return_2"));
        String svg=RouteSchematic.svg(new CalculationNetwork(nodes,branches,List.of(),elements),route,
                Map.of("feed_1",0.000015,"feed_2",0.000015,"return_1",0.00002,"return_2",0.00002));
        assertTrue(svg.contains("feeding=2, return=2"));
        assertTrue(svg.contains("ohm/m"));assertTrue(svg.contains("return_1"));assertTrue(svg.contains("return_2"));
        assertTrue(svg.contains("F_U_S_LEFT"));assertTrue(svg.contains("F_U_S_RIGHT"));
        var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        var lines=document.getElementsByTagName("line");
        for(int j=0;j<lines.getLength();j++) {
            var attrs=lines.item(j).getAttributes();
            assertEquals(attrs.getNamedItem("y1").getNodeValue(),attrs.getNamedItem("y2").getNodeValue());
            String color=attrs.getNamedItem("stroke").getNodeValue();
            assertEquals(color.equals("#b42318")?"160":"400",attrs.getNamedItem("y1").getNodeValue());
        }
    }
    private static CalculationBranch branch(String id,String source,String from,String to) {
        return new CalculationBranch(id,source,from,to,new Real(0.1));
    }
}
