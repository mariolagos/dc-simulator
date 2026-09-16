package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.Route;
import org.supply.math.Real;
import org.supply.solver.model.*;
import java.util.*;
import static org.junit.Assert.*;

public class ElectricalStationSymbolTest {
    @Test public void groupsSixTerminalsAndKeepsLineNamesAndPorts() {
        List<CalculationNode> nodes=new ArrayList<>();
        for(String id:List.of("F_U_S_LEFT","F_U_S_RIGHT","F_D_S_LEFT","F_D_S_RIGHT","R_U_S","R_D_S","F_END","R_END")) {
            nodes.add(new CalculationNode(id,id,"1","U",id.endsWith("END")?1000.0:0.0,CalculationNodeType.GRID_NODE));
        }
        List<CalculationBranch> branches=List.of(
                branch("f","feed_line","F_U_S_RIGHT","F_END"),
                branch("r","return_line","R_U_S","R_END"),
                branch("i1","internal_S","F_U_S_LEFT","F_U_S_RIGHT"),
                branch("i2","internal_S","F_U_S_LEFT","F_D_S_LEFT"),
                branch("i3","internal_S","F_U_S_LEFT","F_D_S_RIGHT"),
                branch("i4","internal_S","R_U_S","R_D_S"));
        List<ElectricalElement> elements=new ArrayList<>(branches);
        elements.add(new DiodeSubstationElement("S","F_U_S_LEFT","R_U_S",new Real(3000),new Real(0.08),true));
        String dot=ElectricalTopologyGraph.dot(new CalculationNetwork(nodes,branches,List.of(),elements),
                new Route("RED",List.of("feed_line"),List.of("return_line")));
        assertTrue(dot.contains("<TABLE"));
        assertTrue(dot.contains("<B>S</B>"));
        assertTrue(dot.contains("feed_line\\n"));
        assertTrue(dot.contains("return_line\\n"));
        assertTrue(dot.contains("F_D_S_RIGHT</FONT>"));
        assertTrue(dot.contains("R_D_S</FONT>"));
        assertFalse(dot.contains("\"F_U_S_LEFT\" ["));
        assertTrue(dot.contains("\"__station_0\":t"));
        assertTrue(dot.contains("dir=none"));
    }
    private static CalculationBranch branch(String id,String source,String from,String to) {
        return new CalculationBranch(id,source,from,to,new Real(0.1));
    }
}
