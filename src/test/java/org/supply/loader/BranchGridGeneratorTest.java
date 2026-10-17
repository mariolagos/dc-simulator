package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.supply.app.GridPreprocessor;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class BranchGridGeneratorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Config fixture(String name) {
        return ConfigFactory.parseResources("branch-grid/" + name).resolve().getConfig("dcsim.grid");
    }
    @Test public void reproducesWEPhysicalLinesAndSubstations() {
        Config input = fixture("WE_grid_branches.conf");
        Config original = fixture("WE_grid_objects.conf");
        Config generated = BranchGridGenerator.generate(input);
        assertTrue(input.hasPath("branches"));
        assertFalse(generated.hasPath("branches"));
        assertEquals(31, generated.getConfig("electricalObjects").root().size());
        assertEquals(30, generated.getConfigList("electricalAssociations").size());
        var before = ElectricalObjectExpander.inspect(original.root().unwrapped());
        var after = ElectricalObjectExpander.inspect(generated.root().unwrapped());
        before.requireCalculationReady(); after.requireCalculationReady();
        assertEquals(physicalLines(before.expandedGrid()), physicalLines(after.expandedGrid()));
        assertEquals(before.expandedGrid().get("power_installations"), after.expandedGrid().get("power_installations"));
        assertEquals(original.getConfig("substationTypes"), generated.getConfig("substationTypes"));
        assertEquals(original.getConfig("lineTypes"), generated.getConfig("lineTypes"));
        for (String key : List.of("u_nominal_V", "u_min_V", "u_cutoff_V", "u_max_V", "i_train_max_A",
                "p_train_max_W", "p_allow_W", "reference_node_id")) assertEquals(original.getValue(key), generated.getValue(key));
    }
    @SuppressWarnings("unchecked")
    private List<String> physicalLines(Map<String, Object> grid) {
        Map<String, String> positions = new HashMap<>();
        for (Map<String, Object> n : (List<Map<String, Object>>) grid.get("nodes"))
            positions.put((String) n.get("node_id"), (String) n.get("position_rwy"));
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> line : (List<Map<String, Object>>) grid.get("lines")) {
            List<String> ends = new ArrayList<>(List.of(positions.get(line.get("node_from_id")), positions.get(line.get("node_to_id"))));
            Collections.sort(ends);
            String id = (String) line.get("line_id");
            lines.add(ends + ":" + id.substring(id.length() - 3) + ":" + line.get("resistance_ohm_per_m"));
        }
        Collections.sort(lines); return lines;
    }
    @Test public void supportsNamedBranchesParallelLinesLoopsAndReversedStation() {
        Config source = fixture("WE_grid_branches.conf");
        Config input = source.withValue("branches", ConfigFactory.parseString("""
            main { section = "101", objects = [P1, { object = SS0, in = R, out = L }, SS1, J1] }
            parallel { section = "101", objects = [P1, SS0] }
            loop { section = "101", objects = [P1, SS1, P1] }
            "200" = [J1, SS2, SS3, J2]
            "102" = [J2, SS4, SS5, P2]
            "201" = [P3, J1]
            "202" = [J2, P4]
            """).root());
        Config result = BranchGridGenerator.generate(input);
        assertTrue(result.getConfigList("electricalAssociations").stream().anyMatch(c ->
                c.getString("from").equals("B__main__1.R") && c.getString("to").equals("SS0.R")));
        ElectricalObjectExpander.inspect(result.root().unwrapped()).requireCalculationReady();
    }
    @Test public void rejectsMissingObjectWrongSectionUnusedObjectAndNameCollision() {
        Config source = fixture("WE_grid_branches.conf");
        reject(source.withValue("branches.101", ConfigValueFactory.fromIterable(List.of("P1", "unknown"))), "Unknown object");
        reject(source.withValue("branches.101", ConfigValueFactory.fromIterable(List.of("P1", "SS2", "J1"))), "not positioned");
        reject(source.withValue("electricalObjects.spare", source.getValue("electricalObjects.P1")), "not used");
        reject(source.withValue("electricalObjects.J1__101", source.getValue("electricalObjects.P1")), "collides");
        reject(source.withValue("branches.101", ConfigValueFactory.fromIterable(List.of("P1", "P1"))), "repeated");
    }
    private void reject(Config input, String message) {
        try { BranchGridGenerator.generate(input); fail("Expected: " + message); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
    @Test public void writesStandaloneConfigAndProtectsExistingOutput() throws Exception {
        Path input = temporary.getRoot().toPath().resolve("input with spaces.conf");
        Path output = temporary.getRoot().toPath().resolve("generated with spaces.conf");
        Config config = ConfigFactory.empty().withValue("dcsim.grid", fixture("WE_grid_branches.conf").root());
        Files.writeString(input, config.root().render());
        GridPreprocessor.main(new String[]{input.toString(), output.toString()});
        Config saved = ConfigFactory.parseFile(output.toFile()).resolve();
        assertFalse(saved.hasPath("dcsim.grid.branches"));
        ElectricalObjectExpander.expand(saved.getConfig("dcsim"));
        String before = Files.readString(output);
        try { GridPreprocessor.main(new String[]{input.toString(), output.toString()}); fail("Expected existing file protection"); }
        catch (FileAlreadyExistsException expected) { assertEquals(before, Files.readString(output)); }
        GridPreprocessor.main(new String[]{input.toString(), output.toString(), "--overwrite"});
        assertEquals(before, Files.readString(output));
    }
    @Test public void namedBranchCanCrossSectionsWithoutChangingPositions() {
        Config types = fixture("WE_grid_branches.conf");
        Config input = ConfigFactory.parseString("""
            reference_node_id = "SS0.N"
            electricalObjects {
                start = { kind = POINT, positions = { N = "23 3+514 N", S = "23 3+514 S" } }
                SS0 = { kind = SUBSTATION, type = standard, positions = { N = "21 2+200 N", S = "21 2+200 S" } }
                end = { kind = POINT, positions = { N = "24 0+450 N", S = "24 0+450 S" } }
            }
            branches { route = [start, SS0, end] }
            """).withValue("lineTypes", types.getValue("lineTypes"))
                .withValue("substationTypes", types.getValue("substationTypes"));
        Config result = BranchGridGenerator.generate(input);
        for (String name : List.of("start", "SS0", "end"))
            assertEquals(input.getValue("electricalObjects." + name + ".positions"),
                    result.getValue("electricalObjects." + name + ".positions"));
        assertEquals(4, result.getConfigList("electricalAssociations").size());
        ElectricalObjectExpander.inspect(result.root().unwrapped()).requireCalculationReady();
        reject(input.withValue("branches", ConfigFactory.parseString("\"23\" = [start, SS0, end]").root()), "not positioned");
        Config we = types.withValue("branches.101", ConfigValueFactory.fromIterable(List.of("P1", "SS0", "SS1", "J1")));
        we = we.withValue("branches.named", we.getValue("branches.101")).withoutPath("branches.101");
        reject(we, "must specify section");
    }
}
