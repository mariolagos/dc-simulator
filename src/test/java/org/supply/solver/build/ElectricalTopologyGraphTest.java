package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.Route;
import org.supply.math.Real;
import org.supply.solver.model.*;
import java.util.*;
import static org.junit.Assert.*;

public class ElectricalTopologyGraphTest {
    @Test public void exportsSelectedLinesAndInternalClosureWithoutCurrentArrows() {
        List<CalculationNode> nodes = new ArrayList<>();
        for (String id : List.of("F_A","F_B","F_C","R_A","R_B","F_OTHER")) {
            nodes.add(new CalculationNode(id,id,"1","U",0.0,CalculationNodeType.GRID_NODE));
        }
        List<CalculationBranch> branches = List.of(
                new CalculationBranch("1","feed\"line","F_A","F_B",new Real(0.1)),
                new CalculationBranch("2","internal_SS","F_B","F_C",new Real(1e-9)),
                new CalculationBranch("3","return","R_A","R_B",new Real(0.1)),
                new CalculationBranch("4","other","F_A","F_OTHER",new Real(0.1)));
        CalculationNetwork network = new CalculationNetwork(nodes,branches,List.of(),new ArrayList<>(branches));
        String dot = ElectricalTopologyGraph.dot(network,new Route("RED",List.of("feed\"line"),List.of("return")));
        assertTrue(dot.contains("feed\\\"line"));
        assertTrue(dot.contains("\"F_C\""));
        assertTrue(dot.contains("style=dashed"));
        assertFalse(dot.contains("F_OTHER"));
        assertTrue(dot.contains("dir=none"));
        assertTrue(dot.contains("rank=same"));
        assertTrue(dot.contains("tooltip="));
        assertTrue(dot.contains("__layout_column_0"));
    }
}
