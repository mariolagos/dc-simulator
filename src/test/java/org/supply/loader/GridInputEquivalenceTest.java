package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.supply.io.export.NetworkInputCsvWriter;
import org.supply.model.GridModel;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Explicit frozen circuits remain independent references for object expansion. */
public class GridInputEquivalenceTest {
    @Test public void weRetainsGeometryLinesSourcesAndCsvExports() throws Exception {
        equivalent("grid-reference/WE.conf","branch-grid/WE_grid_objects.conf",Map.of());
    }
    @Test public void twoSectionsRetainsGeometryLinesSourcesAndCsvExports() throws Exception {
        var mapping=ConfigFactory.parseResources("two-sections-order/mapping.conf");
        Map<String,String> names=new HashMap<>();
        mapping.getConfig("nodes").root().unwrapped().forEach((k,v)->names.put(k,(String)v));
        equivalent("two-sections-order/original.conf","two-sections-order/objects.conf",names);
    }
    private static void equivalent(String reference,String objects,Map<String,String> names) throws Exception {
        var expected=ConfigFactory.parseResources(reference).resolve().getConfig("dcsim");
        var description=ConfigFactory.parseResources(objects).resolve().getConfig("dcsim");
        var loader=new GridModelLoader();var a=loader.load(expected);var b=loader.load(description);
        Map<String,String> nodes=new TreeMap<>();for(var node:a.getNodes())nodes.put(node.getNodeId(),node.getPositionRwy());
        Map<String,String> actual=new TreeMap<>();for(var node:b.getNodes())actual.put(names.getOrDefault(node.getNodeId(),node.getNodeId()),node.getPositionRwy());
        assertEquals(nodes,actual);
        assertEquals(lineSignatures(a,Map.of()),lineSignatures(b,names));
        assertEquals(a.getInstallations().stream().map(i->i.toString()+i.isEnabled()).toList(),b.getInstallations().stream().map(i->i.toString()+i.isEnabled()).toList());
        assertEquals(connectionSignatures(a,Map.of()),connectionSignatures(b,names));
        assertEquals(a.getSwitchGears().size(),b.getSwitchGears().size());
        for(var gear:a.getSwitchGears())assertEquals(gear.cables(),b.getSwitchGears().stream().filter(g->g.installationId().equals(gear.installationId())).findFirst().orElseThrow().cables());
        var expanded=ElectricalObjectExpander.expand(description);
        assertEquals(expanded,ElectricalObjectExpander.expand(expanded));
        if(description.hasPath("traffic"))assertEquals(description.getConfig("traffic"),expanded.getConfig("traffic"));
        Path folder=Files.createTempDirectory("grid-equivalence-");
        try {
            var writer=new NetworkInputCsvWriter();writer.writeLines(expected,folder.resolve("expected.csv"));writer.writeLines(description,folder.resolve("actual.csv"));
            var left=Files.readAllLines(folder.resolve("expected.csv")).stream().sorted().toList();
            var right=new ArrayList<String>();
            for(String row:Files.readAllLines(folder.resolve("actual.csv"))) {
                var columns=row.split(",",-1);for(int i=0;i<2;i++)columns[i]=names.getOrDefault(columns[i],columns[i]);right.add(String.join(",",columns));
            }
            Collections.sort(right);assertEquals(left,right);
            writer.writePowerInstallations(expected,folder.resolve("expected-power.csv"));writer.writePowerInstallations(description,folder.resolve("actual-power.csv"));
            assertEquals(Files.readAllLines(folder.resolve("expected-power.csv")).stream().sorted().toList(),Files.readAllLines(folder.resolve("actual-power.csv")).stream().sorted().toList());
        } finally {try(var files=Files.list(folder)){for(var file:files.toList())Files.delete(file);}Files.delete(folder);}
    }
    private static List<String> lineSignatures(GridModel model,Map<String,String> names) {
        return model.getLines().stream().map(l->names.getOrDefault(l.getFromNode().getNodeId(),l.getFromNode().getNodeId())+" -> "+names.getOrDefault(l.getToNode().getNodeId(),l.getToNode().getNodeId())+" @ "+l.getResistanceOhmPerM().asDouble()).sorted().toList();
    }
    private static List<String> connectionSignatures(GridModel model,Map<String,String> names) {
        return model.getInstallationConnections().stream().map(c->c.getInstallationId()+":"+c.getConnectionType()+":"+names.getOrDefault(c.getNodeId(),c.getNodeId())).sorted().toList();
    }
    @Test public void separateFeedingAndReturnResistancesRemainExplicit() {
        var grid=ElectricalObjectExpanderTest.sketch();var expanded=ElectricalObjectExpander.inspect(grid).expandedGrid();
        var lines=(List<?>)expanded.get("lines");assertEquals(20,lines.size());
        for(var value:lines){var line=(Map<?,?>)value;assertEquals(line.get("line_id").toString().endsWith(".F")?0.000015:0.000020,((Number)line.get("resistance_ohm_per_m")).doubleValue(),0);}
    }
    @Test public void duplicateNodesCannotOverwriteExistingCoordinates() {
        var config=ConfigFactory.parseString("grid {nodes=[{node_id=X,position_rwy=\"1 0+000 N\"},{node_id=X,position_rwy=\"1 1+000 N\"}]}");
        assertThrows(IllegalArgumentException.class,()->new GridModelLoader().load(config));
    }
    @Test public void removedInputVocabulariesAreRejectedRatherThanIgnored() {
        for(String field:List.of("points","links","link_defaults")) {
            var config=ConfigFactory.parseString("grid {nodes=[], "+field+"=[]}");
            assertThrows(IllegalArgumentException.class,()->new GridModelLoader().load(config));
        }
        var config=ConfigFactory.parseString("grid {nodes=[],power_installations=[{installation_id=SS,installation_category=SUBSTATION,installation_model=removed}]}");
        assertThrows(IllegalArgumentException.class,()->new GridModelLoader().load(config));
    }
}
