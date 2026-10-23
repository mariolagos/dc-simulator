package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.supply.app.SwitchGearConnectionReport;
import org.supply.domain.*;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.build.CalculationNetworkBuilder;
import org.supply.solver.electrical.*;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.util.*;
import static org.junit.Assert.*;

public class SwitchGearNoGapTest {
    private Config input(boolean gap) {
        var grid=ConfigFactory.parseResources("switchgear/double-track-gap.conf").resolve().getConfig("dcsim.grid");
        return grid.withValue("substationTypes.standard.switchgear.model",ConfigValueFactory.fromAnyRef(gap?"DOUBLE_TRACK_GAP":"DOUBLE_TRACK_NO_GAP"));
    }
    private Config objects(Config input) { return BranchGridGenerator.generate(input); }
    private GridModel model(Config input) { return new GridModelLoader().load(ConfigFactory.empty().withValue("grid",objects(input).root())); }
    private String node(SwitchGear gear,String id) { return gear.terminals().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow().nodeId(); }
    private CalculationNetwork network(GridModel model) {
        TrackTransformService transform=new TrackTransformService() {
            public ModelCoordinate toModel(String r,RwyCoordinate c) { return new ModelCoordinate(c.getSectionId(),c.getTrackId(),c.getPositionM()); }
            public RwyCoordinate toRailway(String r,ModelCoordinate c) { throw new UnsupportedOperationException(); }
            public ModelCoordinate pathToModel(String r,double p) { throw new UnsupportedOperationException(); }
            public RwyCoordinate pathToRailway(String r,double p) { throw new UnsupportedOperationException(); }
            public double distanceOnRoute(String r,RwyCoordinate a,RwyCoordinate b) { throw new UnsupportedOperationException(); }
        };
        return new CalculationNetworkBuilder(transform).buildBase(model);
    }
    @Test public void continuousRailUsesOneNodePerTrackWithoutArtificialBranch() {
        var gap=model(input(true));var continuous=model(input(false));var gear=continuous.getSwitchGears().get(0);
        assertEquals(node(gear,"1"),node(gear,"3"));assertEquals(node(gear,"2"),node(gear,"4"));
        assertNotEquals(node(gear,"1"),node(gear,"2"));assertNotEquals(node(gear,"1"),node(gear,"R1"));
        assertEquals(gap.getNodes().size()-2,continuous.getNodes().size());
        assertEquals(network(gap).branches().size(),network(continuous).branches().size());
        assertEquals(11,gear.cables().size());
        var lines=continuous.getLines();
        var left=lines.stream().filter(l->l.getLineId().equals("B__101__1.N.F")).findFirst().orElseThrow();
        var right=lines.stream().filter(l->l.getLineId().equals("B__101__2.N.F")).findFirst().orElseThrow();
        assertEquals(left.getToNode().getNodeId(),right.getFromNode().getNodeId());
    }
    @Test public void reversedOrientationKeepsSameTrackContinuity() {
        var config=input(false).withValue("branches.101",ConfigValueFactory.fromIterable(List.of("P1","-SS0","P2")));
        var model=model(config);var gear=model.getSwitchGears().get(0);
        assertEquals(node(gear,"1"),node(gear,"3"));assertEquals(node(gear,"2"),node(gear,"4"));
        assertEquals("101 14+000 S",model.getNode(node(gear,"1")).getPositionRwy());
        assertEquals("101 14+000 N",model.getNode(node(gear,"2")).getPositionRwy());
    }
    @Test public void textReportIdentifiesContinuousNodesWithoutClaimingGap() {
        var config=input(false);var grid=objects(config);
        var text=SwitchGearConnectionReport.render(model(config),grid).get("SS0_connections.txt");
        assertTrue(text.contains("Layout: DOUBLE_TRACK_NO_GAP"));
        assertTrue(text.contains("CONTINUOUS FEEDING (NO GAP)"));
        assertTrue(text.contains("Track N: 1 [SS0.A] | CONTINUOUS | 3 [SS0.A]; distinct_nodes=false"));
        assertFalse(text.contains("| GAP |"));assertFalse(text.contains("FEEDING GAPS"));
    }
    @Test public void solvedVoltageMatchesParallelFeederCircuit() {
        var model=model(input(false));var gear=model.getSwitchGears().get(0);var network=network(model);
        String feeding=node(gear,"1"),returning=node(gear,"R1");
        var system=new AdmittanceSystemBuilder().build(network,node(gear,"NEG"),List.of(
                new CurrentInjection(feeding,Real.fromDouble(-1000)),new CurrentInjection(returning,Real.fromDouble(1000))),Map.of());
        var voltages=new LinearSystemSolver().solveVoltages(system);
        double feedParallel=1/(1/0.0012+1/0.0015);
        assertEquals(750-1000*(0.01+feedParallel+0.0005+0.0002),voltages.get(feeding).asDouble()-voltages.get(returning).asDouble(),1e-6);
    }
    private Config twoFeederInput(boolean gap) {
        var config=input(gap);var cables=new ArrayList<Config>(config.getConfigList("substationTypes.standard.switchgear.connections"));
        cables.removeIf(c->Set.of("FC3","FC4","FP3","FP4").contains(c.getString("id")));
        return config.withValue("substationTypes.standard.switchgear.connections",ConfigValueFactory.fromIterable(cables.stream().map(Config::root).toList()))
                .withoutPath("substationTypes.standard.switchgear.busbars.PBB3").withoutPath("substationTypes.standard.switchgear.busbars.PBB4");
    }
    @Test public void oneFeederPerTrackSuppliesBothRouteSidesOfContinuousRail() {
        var config=twoFeederInput(false);var model=model(config);var gear=model.getSwitchGears().get(0);
        assertEquals(7,gear.cables().size());assertEquals(node(gear,"1"),node(gear,"3"));
        var expanded=ElectricalObjectExpander.expand(ConfigFactory.empty().withValue("grid",objects(config).root()));
        var reloaded=new GridModelLoader().load(ConfigFactory.parseString(expanded.root().render()));
        assertEquals(7,reloaded.getSwitchGears().get(0).cables().size());
    }
    @Test public void sameMissingFeederFailsForGapLayout() {
        assertThrows(IllegalArgumentException.class,()->model(twoFeederInput(true)));
    }
    @Test public void cablesBetweenAliasesAreRejectedAsSelfLoops() {
        var config=input(false);var cables=new ArrayList<Config>(config.getConfigList("substationTypes.standard.switchgear.connections"));
        cables.add(ConfigFactory.parseString("id=BAD,from=\"1\",to=\"3\",length_m=1,conductor_type=cable240,multiplicity=1"));
        var invalid=config.withValue("substationTypes.standard.switchgear.connections",ConfigValueFactory.fromIterable(cables.stream().map(Config::root).toList()));
        assertThrows(IllegalArgumentException.class,()->model(invalid));
    }
    @Test public void routesDoNotChangeWhenFeedingGapIsRemoved() {
        var definitions=ConfigFactory.parseString("X {branches=[\"101\"],track=N}").root();
        var gap=input(true).withValue("routeDefinitions",definitions);var noGap=input(false).withValue("routeDefinitions",definitions);
        assertEquals(BranchRouteGenerator.generate(gap,model(gap)),BranchRouteGenerator.generate(noGap,model(noGap)));
    }
}
