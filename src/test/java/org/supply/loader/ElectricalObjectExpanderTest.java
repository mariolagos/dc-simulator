package org.supply.loader;

import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.model.GridModel;
import java.util.*;
import static org.junit.Assert.*;

public class ElectricalObjectExpanderTest {
    public static Map<String,Object> sketch() {
        return new LinkedHashMap<>(ConfigFactory.parseResources("electrical-objects/branched.conf")
                .resolve().getConfig("dcsim.grid").root().unwrapped());
    }

    private static Map<String,Object> point(String position) {return Map.of("kind","POINT","positions",positions(position));}
    private static Map<String,Object> station(String position) {return Map.of("kind","SUBSTATION","type","standard","positions",positions(position));}
    private static Map<String,Object> positions(String base) {return Map.of("N",base+" N","S",base+" S");}
    private static Map<String,Object> link(String a,String b) {return Map.of("from",a,"to",b);}
    private static List<Map<String,Object>> rows(Object value) {return ((List<?>)value).stream().map(v->{Map<String,Object> r=new LinkedHashMap<>();((Map<?,?>)v).forEach((k,x)->r.put((String)k,x));return r;}).toList();}
    private static Map<String,Object> objects(Map<String,Object> grid) {var out=new LinkedHashMap<String,Object>();((Map<?,?>)grid.get("electricalObjects")).forEach((k,v)->out.put((String)k,v));return out;}
    @Test public void compilesBranchedSketchAndSharesOnlyCorrespondingPointNodes() {
        var result=ElectricalObjectExpander.inspect(sketch());result.requireCalculationReady();
        assertEquals(28,rows(result.expandedGrid().get("nodes")).size());assertEquals(20,rows(result.expandedGrid().get("lines")).size());
        assertEquals(1,result.islands().size());assertEquals(List.of("A1","A2"),result.islands().get(0).enabledSources());
        assertTrue(result.islands().get(0).hasReference());
        var lines=rows(result.expandedGrid().get("lines"));
        for(String role:List.of("N.F","S.F","N.R","S.R")) {
            String a=node(lines,"L2."+role,"node_to_id"),b=node(lines,"L3."+role,"node_from_id"),c=node(lines,"L4."+role,"node_from_id");
            assertEquals(a,b);assertEquals(a,c);
        }
        assertFalse(node(lines,"L2.N.F","node_to_id").equals(node(lines,"L2.N.R","node_to_id")));
        assertFalse(node(lines,"L2.N.F","node_to_id").equals(node(lines,"L2.S.F","node_to_id")));
        var model=new GridModelLoader().load(com.typesafe.config.ConfigValueFactory.fromMap(Map.of("grid",sketch())).toConfig());
        assertEquals(28,model.getNodes().size());assertEquals(20,model.getLines().size());assertEquals(2,model.getSwitchGears().size());
    }
    @Test public void buildsExistingCalculationNetworkWithExpectedResistances() {
        var grid = new GridModelLoader().load(com.typesafe.config.ConfigValueFactory.fromMap(Map.of("grid", sketch())).toConfig());
        var track = new org.supply.track.TrackLoader().load(List.of(
                new org.supply.track.KilometerBoard("1", "0+000", 0, 5000),
                new org.supply.track.KilometerBoard("1", "5+000", 5000, 0)), List.of(), List.of());
        var network = new org.supply.solver.build.CalculationNetworkBuilder(
                new org.supply.track.DefaultTrackTransformService(track)).buildBase(grid);
        assertEquals(28, network.nodes().size());
        assertEquals(28, network.branches().size());
        assertEquals(2, (int) network.elements().stream().filter(e -> e instanceof org.supply.solver.model.DiodeSubstationElement).count());
        var feeding = network.branches().stream().filter(b -> b.sourceId().equals("L1.N.F")).findFirst().orElseThrow();
        var returning = network.branches().stream().filter(b -> b.sourceId().equals("L1.N.R")).findFirst().orElseThrow();
        assertEquals(0.015, feeding.resistanceOhm().asDouble(), 1e-12);
        assertEquals(0.020, returning.resistanceOhm().asDouble(), 1e-12);
    }
    @Test public void disabledSourcesSeparatePotentialBusesAndAutoReferenceAddsNoBranches() {
        var grid = sketch(); grid.remove("reference_node_id");
        var automatic = ElectricalObjectExpander.inspect(grid); automatic.requireCalculationReady();
        assertTrue(automatic.islands().get(0).hasReference());
        assertEquals(20, rows(automatic.expandedGrid().get("lines")).size());
        var objs = objects(grid);
        for (String name : List.of("A1", "A2")) {
            var station = new LinkedHashMap<String,Object>((Map<String,Object>) objs.get(name));
            station.put("enabled", false); objs.put(name, station);
        }
        grid.put("electricalObjects", objs);
        var disabled = ElectricalObjectExpander.inspect(grid);
        assertEquals(2, disabled.islands().size());
        assertTrue(disabled.islands().stream().allMatch(i -> i.enabledSources().isEmpty()));
        try { disabled.requireCalculationReady(); fail("Expected disabled source rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("islands")); }
    }
    private static String node(List<Map<String,Object>> lines,String id,String field) {return (String)lines.stream().filter(l->id.equals(l.get("line_id"))).findFirst().orElseThrow().get(field);}
    @Test public void acceptsDeclaredUnconnectedLineButRejectsCalculation() {
        var grid=sketch();var objs=objects(grid);objs.put("spare",Map.of("kind","LINE","type","standard"));grid.put("electricalObjects",objs);
        var result=ElectricalObjectExpander.inspect(grid);assertFalse(result.incompleteConnections().isEmpty());
        try {result.requireCalculationReady();fail("Expected unconnected line rejection");} catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("spare"));}
    }
    @Test public void detectsSeparatelyPoweredIslandWithoutReference() {
        var grid=sketch();var objs=objects(grid);objs.put("P5",point("2 0+000"));objs.put("A3",station("2 1+000"));objs.put("P6",point("2 2+000"));
        objs.put("L6",Map.of("kind","LINE","type","standard"));objs.put("L7",Map.of("kind","LINE","type","standard"));grid.put("electricalObjects",objs);
        var links=new ArrayList<>(rows(grid.get("electricalAssociations")));links.addAll(List.of(link("P5","L6.L"),link("L6.R","A3.L"),link("A3.R","L7.L"),link("L7.R","P6")));grid.put("electricalAssociations",links);
        var result=ElectricalObjectExpander.inspect(grid);assertEquals(2,result.islands().size());
        var separate=result.islands().stream().filter(i->i.enabledSources().contains("A3")).findFirst().orElseThrow();assertFalse(separate.hasReference());assertTrue(separate.objects().contains("P5"));
        try {result.requireCalculationReady();fail("Expected island rejection");}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("islands"));}
    }
    @Test public void supportsSingleTerminalSpecialConnectionsWithoutImplicitExtras() {
        var grid=sketch();var links=new ArrayList<>(rows(grid.get("electricalAssociations")));links.removeIf(l->"A1.R".equals(l.get("from")));
        links.addAll(List.of(link("A1.B","L2.L.S.F"),link("A1.D","L2.L.N.F"),link("A1.N","L2.L.N.R"),link("A1.S","L2.L.S.R")));grid.put("electricalAssociations",links);
        var result=ElectricalObjectExpander.inspect(grid);result.requireCalculationReady();var lines=rows(result.expandedGrid().get("lines"));
        assertEquals("A1.R.S.F",node(lines,"L2.N.F","node_from_id"));assertEquals("A1.R.N.F",node(lines,"L2.S.F","node_from_id"));assertEquals(20,lines.size());
    }
    @Test public void typeOverridesAndIdentityRemainStableWhenDescriptionsAreReordered() {
        var grid=sketch();var objs=objects(grid);var a=new LinkedHashMap<String,Object>((Map<String,Object>)objs.get("A1"));a.put("emf_V",800.0);a.put("id","SS1");objs.put("A1",a);grid.put("electricalObjects",objs);
        var first=ElectricalObjectExpander.inspect(grid);first.requireCalculationReady();
        assertEquals(800.0,((Number)rows(first.expandedGrid().get("power_installations")).get(0).get("emf_V")).doubleValue(),0);
        var reversed=new LinkedHashMap<String,Object>();var names=new ArrayList<>(objs.keySet());Collections.reverse(names);for(String n:names) reversed.put(n,objs.get(n));grid.put("electricalObjects",reversed);
        var links=new ArrayList<>(rows(grid.get("electricalAssociations")));Collections.reverse(links);links=links.stream().map(l->link((String)l.get("to"),(String)l.get("from"))).collect(java.util.stream.Collectors.toCollection(ArrayList::new));grid.put("electricalAssociations",links);
        assertEquals(first.expandedGrid(),ElectricalObjectExpander.inspect(grid).expandedGrid());
    }
    @Test public void rejectsWrongRolesUnknownReferencesConflictingPositionsAndTypos() {
        var wrong=sketch();wrong.put("electricalAssociations",List.of(link("A1.A","A1.N")));reject(wrong,"feeding with return");
        var unknown=sketch();unknown.put("electricalAssociations",List.of(link("missing","L1.L")));reject(unknown,"Unknown electrical association");
        var conflict=sketch();conflict.put("electricalAssociations",List.of(link("P1","P2")));reject(conflict,"different railway positions");
        var typo=sketch();var objs=objects(typo);var a=new LinkedHashMap<String,Object>((Map<String,Object>)objs.get("A1"));a.put("emff_V",900);objs.put("A1",a);typo.put("electricalObjects",objs);reject(typo,"Unknown field");
    }
    private static void reject(Map<String,Object> grid,String text) {try {ElectricalObjectExpander.inspect(grid);fail("Expected rejection");}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains(text));}}
}
