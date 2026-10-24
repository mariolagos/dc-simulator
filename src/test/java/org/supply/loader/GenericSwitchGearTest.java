package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.supply.domain.*;
import org.supply.model.GridModel;
import java.util.*;
import static org.junit.Assert.*;

/** Contract tests for one explicit wiring vocabulary, without layout selection. */
public class GenericSwitchGearTest {
    private Config input() {return ConfigFactory.parseResources("switchgear/double-track-gap.conf").resolve().getConfig("dcsim.grid");}
    private GridModel load(Config input) {
        return new GridModelLoader().load(ConfigFactory.empty().withValue("grid",BranchGridGenerator.generate(input).root()));
    }
    @Test public void rejectsRemovedLayoutFieldsInsteadOfSilentlyChoosingAnotherModel() {
        for(String field:List.of("model","terminal_ids","branch_terminals")) {
            var spec=input().getConfig("substationTypes.standard.switchgear").withValue(field,ConfigValueFactory.fromAnyRef("obsolete"));
            assertThrows(IllegalArgumentException.class,()->SwitchGearCompiler.terminalIds(spec.root().unwrapped()));
        }
        for(String field:List.of("orientation","branch_positions","terminal_model")) {
            var invalid=input().withValue("electricalObjects.SS0."+field,ConfigValueFactory.fromAnyRef("obsolete"));
            assertThrows(IllegalArgumentException.class,()->load(invalid));
        }
        assertThrows(IllegalArgumentException.class,()->load(input().withValue("branches.101",ConfigValueFactory.fromIterable(List.of("P1","-SS0","P2")))));
    }
    @Test public void arbitraryPortNamesUseExplicitBranchEndpointSelection() {
        var original=input();var pair=original.getConfig("electricalObjects.SS0.positions").root();
        Map<String,Object> ports=Map.of("west",pair.unwrapped(),"east",pair.unwrapped());
        Map<String,Object> names=new LinkedHashMap<>();
        original.getConfig("substationTypes.standard.switchgear.terminals").root().unwrapped().forEach((role,id)->names.put(role.replace("L.","west.").replace("R.","east."),id));
        var config=original.withoutPath("electricalObjects.SS0.positions")
                .withValue("electricalObjects.SS0.ports",ConfigValueFactory.fromMap(ports))
                .withValue("substationTypes.standard.switchgear.terminals",ConfigValueFactory.fromMap(names))
                .withValue("branches.101",ConfigFactory.parseString("x=[P1,{object=SS0,in=west,out=east},P2]").getValue("x"));
        var model=load(config);assertEquals(8,model.getLines().size());
        assertTrue(model.getSwitchGears().get(0).terminals().stream().anyMatch(t->t.id().equals("1")&&t.nodeId().equals("SS0.west.N.F")));
    }
    @Test public void onePortStationAtBranchEndUsesTheSameCompiler() {
        var original=input();var gear=original.getConfig("substationTypes.standard.switchgear").root().unwrapped();
        Map<String,Object> terms=new LinkedHashMap<>();
        original.getConfig("substationTypes.standard.switchgear.terminals").root().unwrapped().forEach((role,id)->{if(role.startsWith("L."))terms.put(role.replace("L.","exit."),id);});
        gear.put("terminals",terms);
        var cables=new ArrayList<Map<String,Object>>();
        for(var cable:original.getConfigList("substationTypes.standard.switchgear.connections")) if(!Set.of("FC3","FC4","FP3","FP4").contains(cable.getString("id")))cables.add(cable.root().unwrapped());
        gear.put("connections",cables);var buses=new LinkedHashMap<>(original.getConfig("substationTypes.standard.switchgear.busbars").root().unwrapped());buses.remove("PBB3");buses.remove("PBB4");gear.put("busbars",buses);
        var config=original.withoutPath("electricalObjects.P1").withoutPath("electricalObjects.SS0.positions")
                .withValue("electricalObjects.SS0.ports",ConfigValueFactory.fromMap(Map.of("exit",original.getConfig("electricalObjects.SS0.positions").root().unwrapped())))
                .withValue("substationTypes.standard.switchgear",ConfigValueFactory.fromMap(gear))
                .withValue("branches.101",ConfigFactory.parseString("x=[{object=SS0,out=exit},P2]").getValue("x"));
        assertEquals(4,load(config).getLines().size());
    }
    @Test public void rejectsMissingPortRoleAndPositionMismatch() {
        var config=input().withoutPath("substationTypes.standard.switchgear.terminals."+ConfigUtil.quoteString("R.S.R"));
        assertThrows(IllegalArgumentException.class,()->load(config));
        var pair=input().getConfig("electricalObjects.SS0.positions").root().unwrapped();
        var bad=new LinkedHashMap<>(pair);bad.put("N","101 14+001 N");
        var moved=input().withoutPath("electricalObjects.SS0.positions").withValue("electricalObjects.SS0.ports",ConfigValueFactory.fromMap(Map.of("L",pair,"R",bad)));
        assertThrows(IllegalArgumentException.class,()->load(moved)); // R1/R2 explicitly shared across L/R.
    }
    @Test public void rejectsUnknownMixedAndRepeatedContinuityTerminals() {
        for(var joins:List.of(List.of(List.of("1","missing")),List.of(List.of("1","R1")),List.of(List.of("1","1")),List.of(List.of("1","3"),List.of("3","1")))) {
            var invalid=input().withValue("substationTypes.standard.switchgear.joins",ConfigValueFactory.fromIterable(joins));
            assertThrows(IllegalArgumentException.class,()->load(invalid));
        }
    }
    @Test public void junctionStationCannotAcquireAnImplicitFeedingBypass() {
        var config=ConfigFactory.parseResources("branch-stations/we-j2.conf").resolve().getConfig("dcsim.grid").withoutPath("electricalObjects.J2.conductors");
        assertThrows(IllegalArgumentException.class,()->load(config));
    }
    @Test public void explicitEmptyJunctionContinuityUsesOnlyStationCables() {
        var config=ConfigFactory.parseResources("branch-stations/we-j2.conf").resolve().getConfig("dcsim.grid")
                .withValue("electricalObjects.J2.conductors",ConfigValueFactory.fromIterable(List.of()));
        var model=load(config);
        assertFalse(model.getLines().stream().anyMatch(l->l.getLineId().startsWith("J__J2__")));
        var routes=BranchRouteGenerator.generate(config,model);assertEquals(4,routes.size());
    }
    @Test public void rejectsConflictingJunctionStationPositions() {
        var config=ConfigFactory.parseResources("branch-stations/we-j2.conf").resolve().getConfig("dcsim.grid")
                .withValue("electricalObjects.SS3.positions",ConfigFactory.parseString("N=\"200 4+500 N\",S=\"200 4+500 S\"").root());
        assertThrows(IllegalArgumentException.class,()->load(config));
    }
    @Test public void sourcePolesMayUseExistingTerminalsWithoutAddingImplicitCableBranches() {
        var grid=ConfigFactory.parseResources("electrical-objects/branched.conf").resolve().getConfig("dcsim.grid");
        var model=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",grid.root()));
        assertEquals(28,model.getNodes().size());
        for(var gear:model.getSwitchGears()) {
            assertEquals(6,gear.terminals().size());assertEquals(4,gear.cables().size());
            var positive=gear.terminals().stream().filter(t->t.id().equals("A")).findFirst().orElseThrow();
            assertTrue(model.getInstallationConnections().stream().anyMatch(c->c.getInstallationId().equals(gear.installationId())
                    &&c.getConnectionType()==ConnectionType.FEEDING&&c.getNodeId().equals(positive.nodeId())));
        }
        var expanded=ElectricalObjectExpander.expand(ConfigFactory.empty().withValue("grid",grid.root()));
        var reloaded=new GridModelLoader().load(ConfigFactory.parseString(expanded.root().render()));
        assertEquals(model.getSwitchGears(),reloaded.getSwitchGears());
    }
    @Test public void rejectsSourcePolarityMismatchAndUnwiredSourcePoint() {
        var grid=ConfigFactory.parseResources("electrical-objects/branched.conf").resolve().getConfig("dcsim.grid");
        for(String pole:List.of("N","unwired")) {
            var invalid=grid.withValue("substationTypes.standard.switchgear.source_terminals.FEEDING",ConfigValueFactory.fromAnyRef(pole));
            assertThrows(IllegalArgumentException.class,()->new GridModelLoader().load(ConfigFactory.empty().withValue("grid",invalid.root())));
        }
    }
}
