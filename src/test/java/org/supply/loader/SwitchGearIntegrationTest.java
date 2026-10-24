package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.supply.domain.*;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.build.CalculationNetworkBuilder;
import org.supply.solver.electrical.*;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.util.*;
import static org.junit.Assert.*;

public class SwitchGearIntegrationTest {
    private Config input() { return ConfigFactory.parseResources("switchgear/double-track-gap.conf").resolve().getConfig("dcsim.grid"); }
    private Config objects(Config input) { return BranchGridGenerator.generate(input); }
    private Config dcsim(Config input) { return ConfigFactory.empty().withValue("grid", objects(input).root()); }
    private GridModel load(Config input) { return new GridModelLoader().load(dcsim(input)); }
    private Config changed(String hocon) { return ConfigFactory.parseString(hocon).withFallback(input()).resolve(); }
    private SwitchGearTerminal terminal(SwitchGear gear, String id) {
        return gear.terminals().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }
    private CalculationNetwork network(GridModel model) {
        TrackTransformService transform = new TrackTransformService() {
            public ModelCoordinate toModel(String route, RwyCoordinate c) { return new ModelCoordinate(c.getSectionId(), c.getTrackId(), c.getPositionM()); }
            public RwyCoordinate toRailway(String r, ModelCoordinate c) { throw new UnsupportedOperationException(); }
            public ModelCoordinate pathToModel(String r, double p) { throw new UnsupportedOperationException(); }
            public RwyCoordinate pathToRailway(String r, double p) { throw new UnsupportedOperationException(); }
            public double distanceOnRoute(String r, RwyCoordinate a, RwyCoordinate b) { throw new UnsupportedOperationException(); }
        };
        return new CalculationNetworkBuilder(transform).buildBase(model);
    }
    @Test public void compilesTwoCableTiersWithoutChangingExternalLines() {
        var model = load(input()); var gear = model.getSwitchGears().get(0);
        assertEquals(8, model.getLines().size());
        assertEquals(11, gear.cables().size());
        assertEquals(13, gear.terminals().size());
        assertEquals(2, model.getInstallationConnections().size());
        assertTrue(model.getSixTerminalRectifierInstallations().isEmpty());
        var net = network(model);
        assertEquals(19, net.branches().size());
        var cable = net.branches().stream().filter(b -> b.id().equals("internal_SS0_FP3")).findFirst().orElseThrow();
        assertEquals(0.0005, cable.resistanceOhm().asDouble(), 1e-15);
        var source = net.elements().stream().filter(e -> e instanceof DiodeSubstationElement)
                .map(e -> (DiodeSubstationElement)e).findFirst().orElseThrow();
        assertEquals("SS0.SG.POS", source.feedingNodeId());
        assertEquals("SS0.SG.NEG", source.returnNodeId());
    }
    @Test public void gapUsesSeparateNodesAtIdenticalRailwayPosition() {
        var model = load(input()); var gear = model.getSwitchGears().get(0);
        String left = terminal(gear,"1").nodeId(), right = terminal(gear,"3").nodeId();
        assertNotEquals(left, right);
        assertEquals(model.getNode(left).getPositionRwy(), model.getNode(right).getPositionRwy());
        assertTrue(network(model).branches().stream().noneMatch(b -> Set.of(b.fromNodeId(),b.toNodeId()).equals(Set.of(left,right))));
    }
    @Test public void cableVoltageDropsMatchAnIndependentSeriesCircuit() {
        var model = load(input()); var gear = model.getSwitchGears().get(0); var net = network(model);
        String feed = terminal(gear,"1").nodeId(), ret = terminal(gear,"R1").nodeId();
        var system = new AdmittanceSystemBuilder().build(net, "SS0.SG.NEG", List.of(
                new CurrentInjection(feed, Real.fromDouble(-1000)),
                new CurrentInjection(ret, Real.fromDouble(1000))), Map.of());
        var voltages = new LinearSystemSolver().solveVoltages(system);
        // 0.01 source + 0.001 FC1 + 0.0002 FP1 + 0.0005 RC0 + 0.0002 RP1.
        assertEquals(750 - 1000 * 0.0119, voltages.get(feed).asDouble()-voltages.get(ret).asDouble(), 1e-6);
    }
    @Test public void explicitTerminalMappingSelectsPhysicalTrackWithoutOrientation() {
        var config=input();var terms=config.getConfig("substationTypes.standard.switchgear.terminals").root().unwrapped();
        Map<String,Object> swapped=new LinkedHashMap<>();
        terms.forEach((role,id)->swapped.put(role.replace(".N.",".X.").replace(".S.",".N.").replace(".X.",".S."),id));
        var model=load(config.withValue("substationTypes.standard.switchgear.terminals",ConfigValueFactory.fromMap(swapped)));
        var gear=model.getSwitchGears().get(0);
        assertTrue(model.getNode(terminal(gear,"1").nodeId()).getPositionRwy().endsWith(" S"));
        assertTrue(model.getNode(terminal(gear,"2").nodeId()).getPositionRwy().endsWith(" N"));
        assertEquals("1",gear.cables().stream().filter(c->c.id().equals("FP1")).findFirst().orElseThrow().toTerminalId());
    }
    @Test public void allowsCustomerTerminalNumbersFiveToEight() {
        var config = input();
        String[] roles={"L.N.F","L.S.F","R.N.F","R.S.F"};
        for(int i=0;i<4;i++) config=config.withValue("substationTypes.standard.switchgear.terminals."+ConfigUtil.quoteString(roles[i]),ConfigValueFactory.fromAnyRef(""+(i+5)));
        var cables=new ArrayList<Map<String,Object>>();
        for(var c:config.getConfigList("substationTypes.standard.switchgear.connections")) {
            var m=new LinkedHashMap<>(c.root().unwrapped());
            if(List.of("1","2","3","4").contains(m.get("to"))) m.put("to",""+(Integer.parseInt((String)m.get("to"))+4));
            cables.add(m);
        }
        config=config.withValue("substationTypes.standard.switchgear.connections",ConfigValueFactory.fromAnyRef(cables));
        var gear=load(config).getSwitchGears().get(0);
        assertEquals("SS0.L.N.F",terminal(gear,"5").nodeId());
        assertEquals("SS0.R.S.F",terminal(gear,"8").nodeId());
    }
    @Test public void rejectsUnknownCableTypeAndFractionalMultiplicity() {
        var cable = input().getConfigList("substationTypes.standard.switchgear.connections").get(0);
        for(var invalid:List.of(cable.withValue("conductor_type",ConfigValueFactory.fromAnyRef("missing")),
                cable.withValue("multiplicity",ConfigValueFactory.fromAnyRef(1.5)))) {
            var list=new ArrayList<Config>(input().getConfigList("substationTypes.standard.switchgear.connections"));list.set(0,invalid);
            var config=input().withValue("substationTypes.standard.switchgear.connections",ConfigValueFactory.fromIterable(list.stream().map(Config::root).toList()));
            assertThrows(IllegalArgumentException.class,()->load(config));
        }
    }
    @Test public void rejectsAnUnwiredExternalTerminal() {
        var list=new ArrayList<Config>(input().getConfigList("substationTypes.standard.switchgear.connections"));
        list.removeIf(c -> c.getString("id").equals("FP1"));
        var config=input().withValue("substationTypes.standard.switchgear.connections",ConfigValueFactory.fromIterable(list.stream().map(Config::root).toList()));
        assertThrows(IllegalArgumentException.class,()->load(config));
    }
    @Test public void generatedGridLoadsWithoutPreprocessorDescriptions() {
        var expanded=ElectricalObjectExpander.expand(dcsim(input()));
        assertFalse(expanded.hasPath("grid.conductorTypes"));
        assertFalse(expanded.hasPath("grid.electricalObjects"));
        var reloaded=new GridModelLoader().load(ConfigFactory.parseString(expanded.root().render()));
        assertEquals(11,reloaded.getSwitchGears().get(0).cables().size());
    }
    @Test public void stationRequiresExplicitWiring() {
        assertThrows(IllegalArgumentException.class,()->load(input().withoutPath("substationTypes.standard.switchgear")));
    }
    @Test public void rejectsOrientationOnPointAndConflictingStationReferences() {
        assertThrows(IllegalArgumentException.class,()->objects(changed("branches.\"101\"=[\"-P1\",SS0,P2]")));
        assertThrows(IllegalArgumentException.class,()->objects(changed("branches.other={section=\"101\",objects=[P1,\"-SS0\",P2]}")));
    }
    @Test public void reloadRejectsCableThatShortsFeedingToReturn() {
        var expanded=ElectricalObjectExpander.expand(dcsim(input()));
        var gear=expanded.getConfigList("grid.switchgears").get(0);
        var cables=new ArrayList<Config>(gear.getConfigList("cables"));
        cables.set(0,cables.get(0).withValue("to",ConfigValueFactory.fromAnyRef("NEG")));
        gear=gear.withValue("cables",ConfigValueFactory.fromIterable(cables.stream().map(Config::root).toList()));
        var invalid=expanded.withValue("grid.switchgears",ConfigValueFactory.fromIterable(List.of(gear.root())));
        assertThrows(IllegalArgumentException.class,()->new GridModelLoader().load(invalid));
    }
    @Test public void reloadRejectsSourceConnectionOutsideSwitchgear() {
        var expanded=ElectricalObjectExpander.expand(dcsim(input()));
        var connections=new ArrayList<Config>(expanded.getConfigList("grid.installation_connections"));
        connections.set(0,connections.get(0).withValue("node_id",ConfigValueFactory.fromAnyRef("P1.N.F")));
        var invalid=expanded.withValue("grid.installation_connections",ConfigValueFactory.fromIterable(connections.stream().map(Config::root).toList()));
        assertThrows(IllegalArgumentException.class,()->new GridModelLoader().load(invalid));
    }
    @Test public void doubledMultiplicityHalvesOnlyTheSpecifiedCableResistance() {
        var expanded=ElectricalObjectExpander.expand(dcsim(input()));
        var gear=expanded.getConfigList("grid.switchgears").get(0);
        var cables=new ArrayList<Config>(gear.getConfigList("cables"));
        cables.set(0,cables.get(0).withValue("multiplicity",ConfigValueFactory.fromAnyRef(4)));
        gear=gear.withValue("cables",ConfigValueFactory.fromIterable(cables.stream().map(Config::root).toList()));
        var changed=expanded.withValue("grid.switchgears",ConfigValueFactory.fromIterable(List.of(gear.root())));
        var net=network(new GridModelLoader().load(changed));
        assertEquals(0.0005,net.branches().stream().filter(b->b.id().equals("internal_SS0_FC1")).findFirst().orElseThrow().resistanceOhm().asDouble(),1e-15);
        assertEquals(0.001,net.branches().stream().filter(b->b.id().equals("internal_SS0_FC2")).findFirst().orElseThrow().resistanceOhm().asDouble(),1e-15);
    }

}
