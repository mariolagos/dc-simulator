package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.*;
import java.util.*;
import static org.junit.Assert.*;

public class IdealConnectionSolverTest {
    @Test public void exactNodeReductionMatchesAnalyticalTwoWireCircuit() {
        List<CalculationNode> nodes=new ArrayList<>();for(String id:List.of("P","N","F","R","TF","TR"))nodes.add(new CalculationNode(id,id,"101","N",0,CalculationNodeType.GRID_NODE));
        var f=new CalculationBranch("f","f","F","TF",Real.fromDouble(1));
        var r=new CalculationBranch("r","r","R","TR",Real.fromDouble(1));
        var source=new DiodeSubstationElement("S","P","N",Real.fromDouble(750),Real.fromDouble(.1),true);
        var network=new CalculationNetwork(nodes,List.of(f,r),List.of(),List.of(f,r,source,new IdealConnectionElement("pf","P","F"),new IdealConnectionElement("nr","N","R")));
        var system=new AdmittanceSystemBuilder().build(network,"R",List.of(new CurrentInjection("TF",Real.fromDouble(-10)),new CurrentInjection("TR",Real.fromDouble(10))));
        assertEquals(4,system.size());assertEquals(system.nodeIndex("N"),system.nodeIndex("R"));
        var v=new LinearSystemSolver().solveVoltages(system);
        assertEquals(749,v.get("P").asDouble(),1e-9);assertEquals(v.get("P"),v.get("F"));assertEquals(0,v.get("N").asDouble(),0);
        assertEquals(729,v.get("TF").asDouble()-v.get("TR").asDouble(),1e-9);
    }
}
