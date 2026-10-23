package org.supply.loader;
import com.typesafe.config.*;
import org.junit.Test;
import org.supply.domain.*;
import org.supply.model.GridModel;
import org.supply.solver.build.*;
import org.supply.solver.model.*;
import org.supply.track.*;
import org.supply.app.SwitchGearConnectionReport;
import java.util.*;
import static org.junit.Assert.*;
public class BranchSwitchGearJ2Test {
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

    @Test public void sameModelSupportsBothJunctionsAndAllFourRoutes() {
        var input=ConfigFactory.parseResources("branch-stations/we-j2.conf").resolve().getConfig("dcsim.grid");
        var generated=BranchGridGenerator.generate(input);
        var model=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",generated.root()));
        var gear=model.getSwitchGears().stream().filter(g->g.installationId().equals("SS3")).findFirst().orElseThrow();
        Set<String> feeding=new HashSet<>();
        for(int i=1;i<=6;i++) {
            final String id=""+i;
            var terminal=gear.terminals().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow();
            var p=RwyCoordinateParser.parse(model.getNode(terminal.nodeId()).getPositionRwy());
            assertEquals(i<=2?"200":i<=4?"202":"102",p.getSectionId());
            assertEquals(i%2==1?"N":"S",p.getTrackId());feeding.add(terminal.nodeId());
        }
        assertEquals(6,feeding.size());assertEquals(19,gear.cables().size());assertEquals(6,model.getInstallations().size());
        for(String junction:List.of("J1","J2")) {
            assertFalse(model.getLines().stream().anyMatch(l->l.getLineId().startsWith("J__"+junction+"__")&&l.getLineId().endsWith(".F")));
            assertEquals(4,model.getLines().stream().filter(l->l.getLineId().startsWith("J__"+junction+"__")&&l.getLineId().endsWith(".R")).count());
        }
        var routes=new RouteFactory().build(ConfigFactory.empty().withValue("routes",ConfigValueFactory.fromMap(BranchRouteGenerator.generate(input,model))));
        var network=network(model);var check=ElectricalRouteCheck.inspect(network,routes,null);
        assertEquals(List.of(),check.errors());assertEquals(List.of(),check.warnings());assertEquals(4,routes.size());
        Map<String,RwyCoordinate> positions=new HashMap<>();model.getNodes().forEach(n->positions.put(n.getNodeId(),RwyCoordinateParser.parse(n.getPositionRwy())));
        Map<String,Double> resistance=new HashMap<>();model.getLines().forEach(l->resistance.put(l.getLineId(),l.getResistanceOhmPerM().asDouble()));
        for(var route:routes) {
            var svg=RouteSchematic.svg(network,route,resistance,new RouteSchematic.Context(positions,List.of()));
            assertTrue(route.id(),svg.contains(">SS1<"));assertTrue(route.id(),svg.contains(">SS3<"));
        }
    }
}
