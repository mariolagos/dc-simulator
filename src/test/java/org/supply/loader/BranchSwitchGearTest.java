package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.supply.app.SwitchGearConnectionReport;
import org.supply.domain.*;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.build.*;
import org.supply.solver.electrical.*;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.util.*;
import static org.junit.Assert.*;

public class BranchSwitchGearTest {
    private Config input() { return ConfigFactory.parseResources("branch-stations/we-j1.conf").resolve().getConfig("dcsim.grid"); }
    private Config objects(Config input) { return BranchGridGenerator.generate(input); }
    private GridModel model(Config input) { return new GridModelLoader().load(ConfigFactory.empty().withValue("grid",objects(input).root())); }
    private SwitchGear gear(GridModel model) { return model.getSwitchGears().stream().filter(g->g.installationId().equals("SS1")).findFirst().orElseThrow(); }
    private String node(SwitchGear gear,String id) { return gear.terminals().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow().nodeId(); }
    private CalculationNetwork network(GridModel model) {
        TrackTransformService transform=new TrackTransformService() {
            public ModelCoordinate toModel(String r,RwyCoordinate c) { return new ModelCoordinate(c.getSectionId(),c.getTrackId(),c.getPositionM()); }
            public RwyCoordinate toRailway(String r,ModelCoordinate c) { throw new UnsupportedOperationException(); }
            public ModelCoordinate pathToModel(String r,double p) { throw new UnsupportedOperationException(); }
            public RwyCoordinate pathToRailway(String r,double p) { throw new UnsupportedOperationException(); }
            public double distanceOnRoute(String r,RwyCoordinate a,RwyCoordinate b) { throw new UnsupportedOperationException(); }
            public List<ModelCoordinate> linePath(ModelCoordinate a,ModelCoordinate b) { return List.of(a,b); }
        };
        return new CalculationNetworkBuilder(transform).buildBase(model);
    }
    private Config isolated() {
        var source=input();var old=source.getConfig("electricalObjects");
        Map<String,Object> objects=new LinkedHashMap<>();
        for(String name:List.of("P1","P3","J1","SS1")) objects.put(name,old.getConfig(name).root().unwrapped());
        objects.put("PR",ConfigFactory.parseString("kind=POINT\npositions {N=\"200 6+200 N\",S=\"200 6+200 S\"}").root().unwrapped());
        return source.withValue("electricalObjects",ConfigValueFactory.fromMap(objects))
                .withValue("branches",ConfigFactory.parseString("\"101\"=[P1,J1]\n\"200\"=[J1,PR]\n\"201\"=[P3,J1]").root())
                .withValue("reference_node_id",ConfigValueFactory.fromAnyRef("SS1.R_101_N"))
                .withValue("routeDefinitions",ConfigFactory.parseString("X {branches=[\"101\",\"200\"],track=N}\nY {branches=[\"201\",\"200\"],track=N}").root());
    }
    @Test public void mapsConfirmedSixTerminalsToThreeSections() {
        var model=model(input());var gear=gear(model);
        for(int i=1;i<=6;i++) {
            var coordinate=RwyCoordinateParser.parse(model.getNode(node(gear,""+i)).getPositionRwy());
            assertEquals(i<=2?"101":i<=4?"200":"201",coordinate.getSectionId());
            assertEquals(i%2==1?"N":"S",coordinate.getTrackId());
        }
        assertEquals(6,new HashSet<>(List.of(node(gear,"1"),node(gear,"2"),node(gear,"3"),node(gear,"4"),node(gear,"5"),node(gear,"6"))).size());
        assertEquals(19,gear.cables().size());assertEquals(6,model.getInstallations().size());
    }
    @Test public void removesAutomaticFeedingBypassButRetainsReturnJunctionLinksAndPoints() {
        var generated=objects(input());var model=model(input());
        assertEquals("POINT",generated.getString("electricalObjects.J1__101.kind"));
        assertEquals("POINT",generated.getString("electricalObjects.J1__200.kind"));
        assertEquals("POINT",generated.getString("electricalObjects.J1__201.kind"));
        assertTrue(model.getLines().stream().noneMatch(l->l.getLineId().startsWith("J__J1__")&&l.getLineId().endsWith(".F")));
        assertEquals(4,model.getLines().stream().filter(l->l.getLineId().startsWith("J__J1__")&&l.getLineId().endsWith(".R")).count());
        assertTrue(model.getLines().stream().anyMatch(l->l.getLineId().equals("J__J2__1.N.F")));
    }
    @Test public void cutStationCablesDisconnectsFeedingBetweenBranches() {
        var model=model(isolated());var gear=gear(model);var network=network(model);
        var outside=network.branches().stream().filter(b->!b.sourceId().equals("internal_SS1")).toList();
        assertFalse(connected(outside,node(gear,"1"),node(gear,"3")));
        assertFalse(connected(outside,node(gear,"1"),node(gear,"5")));
        assertTrue(connected(outside,node(gear,"R_101_N"),node(gear,"R_201_N")));
    }
    private boolean connected(List<CalculationBranch> branches,String start,String end) {
        Map<String,Set<String>> graph=new HashMap<>();
        for(var b:branches) { graph.computeIfAbsent(b.fromNodeId(),k->new HashSet<>()).add(b.toNodeId()); graph.computeIfAbsent(b.toNodeId(),k->new HashSet<>()).add(b.fromNodeId()); }
        Set<String> seen=new HashSet<>();Deque<String> queue=new ArrayDeque<>();queue.add(start);
        while(!queue.isEmpty()) {String node=queue.removeFirst();if(node.equals(end)) return true;if(seen.add(node))queue.addAll(graph.getOrDefault(node,Set.of()));}
        return false;
    }
    @Test public void allFourGeneratedRoutesPassElectricalTopologyInspection() {
        var input=input();var model=model(input);
        var routes=new RouteFactory().build(ConfigFactory.empty().withValue("routes",ConfigValueFactory.fromMap(BranchRouteGenerator.generate(input,model))));
        var check=ElectricalRouteCheck.inspect(network(model),routes,null);
        assertEquals(List.of(),check.errors());assertEquals(List.of(),check.warnings());
        for(var route:routes) assertTrue(route.feedingLineIds().stream().noneMatch(id->id.startsWith("J__J1__")));
        assertTrue(routes.stream().filter(r->r.id().equals("WE")).findFirst().orElseThrow().returnLineIds().contains("J__J1__1.N.R"));
    }
    @Test public void loadedBranchProducesExpectedVoltageDropWithoutFeedingBypass() {
        var model=model(isolated());var gear=gear(model);var network=network(model);
        var system=new AdmittanceSystemBuilder().build(network,node(gear,"NEG"),List.of(
                new CurrentInjection(node(gear,"5"),Real.fromDouble(-1000)),
                new CurrentInjection(node(gear,"R_201_N"),Real.fromDouble(1000))),Map.of());
        var voltages=new LinearSystemSolver().solveVoltages(system);
        // One feeding cable path, common return trunk and three nearly parallel N return cables.
        double expected=3120-1000*(0.08+0.001+0.0002+0.0005+0.0002/3);
        assertEquals(expected,voltages.get(node(gear,"5")).asDouble()-voltages.get(node(gear,"R_201_N")).asDouble(),0.01);
    }
    @Test public void reportAndExpandedReloadPreserveTerminalIdentityAndAllCables() {
        var input=input();var grid=objects(input);var model=model(input);
        var reports=SwitchGearConnectionReport.render(model,grid);
        assertTrue(reports.get("SS1_connections.txt").contains("101.N.F | 1 | SS1.101.N.F | 101 18+750 N"));
        assertTrue(reports.get("SS1_connections.txt").contains("201.S.F | 6 | SS1.201.S.F | 201 14+000 S"));
        assertTrue(reports.get("SS1_connections.txt").contains("BRANCH FEEDING GAPS"));
        assertEquals(20,reports.get("SS1_connections.csv").lines().count());
        var expanded=ElectricalObjectExpander.expand(ConfigFactory.empty().withValue("grid",grid.root()));
        var reloaded=new GridModelLoader().load(ConfigFactory.parseString(expanded.root().render()));
        assertEquals(gear(model).terminals(),gear(reloaded).terminals());assertEquals(gear(model).cables(),gear(reloaded).cables());
    }
    @Test public void rejectsMissingOrDuplicateBranchTerminalMappings() {
        assertThrows(IllegalArgumentException.class,()->model(input().withoutPath("switchgearTypes.branch_j1.branch_terminals.201")));
        assertThrows(IllegalArgumentException.class,()->model(input().withValue("switchgearTypes.branch_j1.branch_terminals.201.N",ConfigValueFactory.fromAnyRef("1"))));
    }
    @Test public void rejectsMissingFeederCable() {
        var input=input();var cables=new ArrayList<Config>(input.getConfigList("switchgearTypes.branch_j1.connections"));
        cables.removeIf(c->c.getString("id").equals("FP5"));
        var invalid=input.withValue("switchgearTypes.branch_j1.connections",ConfigValueFactory.fromIterable(cables.stream().map(Config::root).toList()));
        assertThrows(IllegalArgumentException.class,()->model(invalid));
    }
    @Test public void rejectsStationInChainAndConflictingOwners() {
        assertThrows(IllegalArgumentException.class,()->model(input().withValue("branches.101",ConfigValueFactory.fromIterable(List.of("P1","-SS0","SS1","J1")))));
        var station=input().getConfig("electricalObjects.SS1");
        assertThrows(IllegalArgumentException.class,()->model(input().withValue("electricalObjects.SS2",station.root())));
    }
    @Test public void rejectsJunctionInMiddleOfOwnedBranch() {
        var config=isolated().withValue("electricalObjects.PX",ConfigFactory.parseString("kind=POINT\npositions {N=\"101 19+000 N\",S=\"101 19+000 S\"}").root())
                .withValue("branches.101",ConfigValueFactory.fromIterable(List.of("P1","J1","PX")));
        assertThrows(IllegalArgumentException.class,()->model(config));
    }
    @Test public void allRouteSchematicsAcceptStationTerminalsOnUnselectedThirdLeg() {
        var input=input();var model=model(input);var network=network(model);
        var routes=new RouteFactory().build(ConfigFactory.empty().withValue("routes",ConfigValueFactory.fromMap(BranchRouteGenerator.generate(input,model))));
        Map<String,RwyCoordinate> coordinates=new HashMap<>();
        model.getNodes().forEach(n->coordinates.put(n.getNodeId(),RwyCoordinateParser.parse(n.getPositionRwy())));
        Map<String,Double> resistances=new HashMap<>();model.getLines().forEach(l->resistances.put(l.getLineId(),l.getResistanceOhmPerM().asDouble()));
        var context=new RouteSchematic.Context(coordinates,List.of());
        for(var route:routes) {
            String svg=RouteSchematic.svg(network,route,resistances,context);
            assertTrue(svg.contains("SS1"));assertTrue(svg.endsWith("</g></svg>\n"));
        }
        assertTrue(RouteSchematic.combinedSvg(network,routes,resistances,context).contains("SS1"));
    }

}
