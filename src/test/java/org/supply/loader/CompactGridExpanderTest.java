package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.io.export.NetworkInputCsvWriter;
import org.supply.model.GridModel;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class CompactGridExpanderTest {
    @Test public void weRetainsNodesLinesInstallationsAndExportedConnections() throws Exception { equivalent("WE"); }
    @Test public void twoSectionsRetainsNodesLinesInstallationsAndTraffic() throws Exception { equivalent("two_sections"); }
    private static void equivalent(String name) throws Exception {
        Config old=ConfigFactory.parseResources("compact-grid/"+name+"_explicit.conf").resolve().getConfig("dcsim");
        Config compact=ConfigFactory.parseResources("compact-grid/"+name+"_compact.conf").resolve().getConfig("dcsim");
        var loader=new GridModelLoader();
        GridModel a=loader.load(old),b=loader.load(compact);
        assertEquals(nodeSignatures(a),nodeSignatures(b));
        assertEquals(lineSignatures(a),lineSignatures(b));
        assertEquals(a.getSixTerminalRectifierInstallations(),b.getSixTerminalRectifierInstallations());
        assertEquals(a.getInstallations().stream().map(i->i.toString()+i.isEnabled()).toList(),
                b.getInstallations().stream().map(i->i.toString()+i.isEnabled()).toList());
        assertEquals(a.getInstallationConnections().stream().map(Object::toString).sorted().toList(),
                b.getInstallationConnections().stream().map(Object::toString).sorted().toList());
        Config expanded=CompactGridExpander.expand(compact);
        assertEquals(expanded.root().unwrapped(),CompactGridExpander.expand(expanded).root().unwrapped());
        if(old.hasPath("traffic")) assertEquals(old.getConfig("traffic").root().unwrapped(),expanded.getConfig("traffic").root().unwrapped());
        Path directory=Files.createTempDirectory("compact-grid-");
        try {
            var writer=new NetworkInputCsvWriter();
            writer.writeLines(old,directory.resolve("old.csv"));
            writer.writeLines(compact,directory.resolve("compact.csv"));
            assertEquals(Files.readAllLines(directory.resolve("old.csv")).stream().sorted().toList(),
                    Files.readAllLines(directory.resolve("compact.csv")).stream().sorted().toList());
        } finally {
            try(var files=Files.list(directory)) { for(var file:files.toList()) Files.delete(file); }
            Files.delete(directory);
        }
    }
    private static Map<String,String> nodeSignatures(GridModel grid) {
        Map<String,String> result=new TreeMap<>();
        for(var n:grid.getNodes()) result.put(n.getNodeId(),n.getPositionRwy());
        return result;
    }
    private static Map<String,String> lineSignatures(GridModel grid) {
        Map<String,String> result=new TreeMap<>();
        for(var l:grid.getLines()) result.put(l.getLineId(),l.getFromNode().getNodeId()+" -> "+
                l.getToNode().getNodeId()+" @ "+l.getResistanceOhmPerM().asDouble());
        assertEquals(grid.getLines().size(),result.size());return result;
    }
    private static Map<String,Object> smallGrid() {
        return new LinkedHashMap<>(Map.of("points",List.of(
                        Map.of("point_id","A","positions",Map.of("NORTH","1 0+000 N","SOUTH","1 0+000 S")),
                        Map.of("point_id","B","positions",Map.of("NORTH","1 1+000 N","SOUTH","1 1+000 S"))),
                "link_defaults",Map.of("resistance_ohm_per_m",0.000015),
                "links",List.of(Map.of("from","A","to","B"))));
    }
    @Test public void generatesFourWiresWithIndependentFeedAndReturnResistance() {
        var grid=smallGrid();grid.put("link_defaults",Map.of("feeding_resistance_ohm_per_m",0.000015,"return_resistance_ohm_per_m",0.000020));
        var expanded=CompactGridExpander.expandGrid(grid);
        var lines=(List<?>)expanded.get("lines");assertEquals(4,lines.size());
        for(var value:lines) {
            var line=(Map<?,?>)value;
            assertEquals(line.get("line_id").toString().startsWith("F_")?0.000015:0.000020,
                    ((Number)line.get("resistance_ohm_per_m")).doubleValue(),0);
        }
    }
    @Test public void rejectsUnknownEndpointsDuplicateIdsAndInvalidResistance() {
        var unknown=smallGrid();unknown.put("links",List.of(Map.of("from","A","to","missing")));reject(unknown,"Unknown grid link endpoint");
        var duplicate=smallGrid();duplicate.put("nodes",List.of(Map.of("node_id","F_A_N","position_rwy","1 0+000 N")));reject(duplicate,"Duplicate node_id");
        var repeated=smallGrid();repeated.put("links",List.of(Map.of("from","A","to","B"),Map.of("from","A","to","B")));reject(repeated,"Duplicate line_id");
        var invalid=smallGrid();invalid.put("link_defaults",Map.of("resistance_ohm_per_m",Double.NaN));reject(invalid,"Positive finite");
        var wrongIds=smallGrid();wrongIds.put("links",List.of(Map.of("from","A","to","B","feeding_line_ids",List.of("only-one"))));reject(wrongIds,"Line ID list");
    }
    private static void reject(Map<String,Object> grid,String message) {
        try { CompactGridExpander.expandGrid(grid);fail("Expected rejection: "+message); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage(),expected.getMessage().contains(message)); }
    }
}
