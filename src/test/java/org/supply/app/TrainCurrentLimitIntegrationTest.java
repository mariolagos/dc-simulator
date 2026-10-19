package org.supply.app;

import org.junit.Test;
import org.supply.domain.*;
import org.supply.loader.TrainCurrentLimitFactory;
import com.typesafe.config.ConfigFactory;
import org.supply.math.Real;
import org.supply.solver.build.TrainNodeInserter;
import org.supply.solver.model.*;
import java.util.*;
import static org.junit.Assert.*;

public class TrainCurrentLimitIntegrationTest {
    private static final SystemParameters PARAMETERS = new SystemParameters(750,500,600,900,300,10000000,3600000,"R0");
    @Test public void usesIndependentLimitsInSolverIterationsAndReportedPower() throws Exception {
        Map<String,Double> limits = new TrainCurrentLimitFactory().build(ConfigFactory.parseString("""
                templates { single {i_train_max_A=100}, double {i_train_max_A=200}, basic {} }
                timetable.trains=[{id=S,template_id=single,count=1},{id=D,template_id=double,count=1},{id=F,template_id=basic,count=1}]
                """), PARAMETERS.iMaxA());
        List<CalculationNode> nodes = List.of(node("F0",0),node("F1",1000),node("R0",0),node("R1",1000));
        List<CalculationBranch> branches = List.of(new CalculationBranch("feed","feed","F0","F1",new Real(.01)),
                new CalculationBranch("return","return","R0","R1",new Real(.01)));
        List<ElectricalElement> elements = new ArrayList<>(branches);
        elements.add(new DiodeSubstationElement("SS","F0","R0",new Real(750),new Real(.1),true));
        CalculationNetwork base = new CalculationNetwork(nodes,branches,List.of(),elements);
        List<CalculationTrainPosition> trains = List.of(train("S",200),train("D",500),train("F",800));
        CalculationNetwork network = new TrainNodeInserter(PARAMETERS,
                List.of(new Route("route",List.of("feed"),List.of("return"))), limits).insertTrainNodes(base,trains);
        Map<String,Double> requested = Map.of("S",1000000.,"D",1000000.,"F",1000000.);
        var solved = DcSolver.solveWithFallback(PARAMETERS,network,requested,Map.of());
        assertTrue(solved.networkResult().converged());
        var volts = solved.networkResult().voltages();
        double supplyCurrent = (750-volts.get("F0").asDouble())/.1;
        assertEquals(600,supplyCurrent,1e-4);
        for (var load : network.trainLoads()) {
            double limit = limits.get(load.trainId());
            double outgoing = 0;
            double v = volts.get(load.feedingNodeId()).asDouble();
            for (var b : network.branches()) {
                if (b.fromNodeId().equals(load.feedingNodeId())) outgoing += (v-volts.get(b.toNodeId()).asDouble())/b.resistanceOhm().asDouble();
                if (b.toNodeId().equals(load.feedingNodeId())) outgoing += (v-volts.get(b.fromNodeId()).asDouble())/b.resistanceOhm().asDouble();
            }
            assertEquals(limit,-outgoing,1e-4);
        }
        // Verify the actual power snapshot used by result and energy reporting.
        var snapshotMethod = DcSolver.class.getDeclaredMethod("powerSnapshot",CalculationNetwork.class,Map.class,Map.class,SystemParameters.class);
        snapshotMethod.setAccessible(true);
        Object snapshot = snapshotMethod.invoke(null,network,volts,requested,PARAMETERS);
        var powersMethod = snapshot.getClass().getDeclaredMethod("trainPowersW"); powersMethod.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Double> powers = (Map<String,Double>) powersMethod.invoke(snapshot);
        for(var load:network.trainLoads()) {
            double u = volts.get(load.feedingNodeId()).asDouble()-volts.get(load.returnNodeId()).asDouble();
            assertEquals(u*limits.get(load.trainId()),powers.get(load.trainId()),1e-5);
        }
    }
    @Test public void keepsVoltageDependentCurvesThroughIterationsAndReporting() throws Exception {
        var single = org.supply.solver.model.TrainCurrentCurveTest.single();
        var coupled = org.supply.solver.model.TrainCurrentCurveTest.coupled();
        var curves = Map.of("S",single,"D",coupled);
        var limits = Map.of("S",6000.,"D",7000.);
        var nodes = List.of(node("F0",0),node("F1",1000),node("R0",0),node("R1",1000));
        var branches = List.of(new CalculationBranch("f","feed","F0","F1",new Real(.0001)),
                new CalculationBranch("r","return","R0","R1",new Real(.0001)));
        List<ElectricalElement> elements = new ArrayList<>(branches);
        elements.add(new DiodeSubstationElement("SS","F0","R0",new Real(580),new Real(.0001),true));
        var base = new CalculationNetwork(nodes,branches,List.of(),elements);
        var network = new TrainNodeInserter(PARAMETERS,
                List.of(new Route("route",List.of("feed"),List.of("return"))),limits,curves)
                .insertTrainNodes(base,List.of(train("S",200),train("D",800)));
        var requested = Map.of("S",1e7,"D",1e7);
        var solved = DcSolver.solveWithFallback(PARAMETERS,network,requested,Map.of());
        assertTrue(solved.networkResult().converged());
        var volts=solved.networkResult().voltages();
        var method=DcSolver.class.getDeclaredMethod("powerSnapshot",CalculationNetwork.class,Map.class,Map.class,SystemParameters.class);
        method.setAccessible(true);Object snapshot=method.invoke(null,network,volts,requested,PARAMETERS);
        var getP=snapshot.getClass().getDeclaredMethod("trainPowersW");getP.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Double> powers=(Map<String,Double>)getP.invoke(snapshot);
        for(var load:network.trainLoads()) {
            double v=volts.get(load.feedingNodeId()).asDouble();
            double u=v-volts.get(load.returnNodeId()).asDouble();
            assertTrue(u>560 && u<600);
            double expected=curves.get(load.trainId()).currentLimitA(u);
            double outgoing=0;
            for(var b:network.branches()) {
                if(b.fromNodeId().equals(load.feedingNodeId()))outgoing+=(v-volts.get(b.toNodeId()).asDouble())/b.resistanceOhm().asDouble();
                if(b.toNodeId().equals(load.feedingNodeId()))outgoing+=(v-volts.get(b.fromNodeId()).asDouble())/b.resistanceOhm().asDouble();
            }
            assertEquals(expected,-outgoing,.2);
            assertEquals(expected*u,powers.get(load.trainId()),1e-5);
        }
    }
    @Test public void appliesVoltageDeratingToEachLimitAndPreservesLegacyDefault() {
        assertEquals(50,new TrainLoadElement("S","F","R",1e6,550,PARAMETERS,100.).currentA(),1e-9);
        assertEquals(200,new TrainLoadElement("D","F","R",1e6,750,PARAMETERS,200.).currentA(),1e-9);
        assertEquals(300,new TrainLoadElement("F","F","R",1e6,750,PARAMETERS).currentA(),1e-9);
        assertEquals(0,new TrainLoadElement("S","F","R",1e6,500,PARAMETERS,100.).currentA(),1e-9);
    }
    private static CalculationNode node(String id,double p) { return new CalculationNode(id,id,"1","N",p,CalculationNodeType.GRID_NODE); }
    private static CalculationTrainPosition train(String id,double p) {return new CalculationTrainPosition(id,"1","N","route",p,p,new Real(1e6));}
}
