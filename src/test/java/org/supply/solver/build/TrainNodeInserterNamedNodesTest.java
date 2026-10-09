package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.Route;
import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.model.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import static org.junit.Assert.*;

public class TrainNodeInserterNamedNodesTest {
    private static final SystemParameters PARAMETERS = new SystemParameters(750, 500, 600, 900, 6000, 10000000, 3600000);
    private static List<Route> routes() { return List.of(new Route("AD", List.of("L02.N.F"), List.of("L02.N.R"))); }
    private static CalculationNode node(String id, double position) {
        return new CalculationNode(id, id, "101", "N", position, CalculationNodeType.GRID_NODE);
    }
    private static CalculationNetwork network() {
        var nodes = List.of(node("SS1.B", 1000), node("J101.N.F", 0), node("SS1.N", 1000),
                node("J101.N.R", 0), node("SS1.S", 1000));
        var branches = List.of(new CalculationBranch("feed", "L02.N.F", "SS1.B", "J101.N.F", Real.fromDouble(0.015)),
                new CalculationBranch("return", "L02.N.R", "SS1.N", "J101.N.R", Real.fromDouble(0.015)),
                new CalculationBranch("internal", "internal_SS1", "SS1.N", "SS1.S", Real.fromDouble(1e-9)));
        return new CalculationNetwork(nodes, branches, List.of(), List.copyOf(branches));
    }
    private static CalculationTrainPosition train(double position) {
        return new CalculationTrainPosition("ad-001", "101", "N", "AD", 2000 - position, position, Real.fromDouble(10000));
    }
    @Test public void placesReported608MetreFailureUsingRouteRoles() {
        var result = new TrainNodeInserter(PARAMETERS, routes()).insertTrainNodes(network(), List.of(train(608)));
        assertEquals(1, result.trainLoads().size()); assertEquals(5, result.branches().size());
        assertEquals("train_ad-001_F", result.trainLoads().get(0).feedingNodeId());
        assertEquals("train_ad-001_R", result.trainLoads().get(0).returnNodeId());
        assertTrue(result.branches().contains(network().branches().get(2)));
        double feedResistance = result.branches().stream().filter(b -> b.sourceId().equals("L02.N.F"))
                .mapToDouble(b -> b.resistanceOhm().asDouble()).sum();
        assertEquals(0.015, feedResistance, 1e-12);
    }
    @Test public void placesTrainAtNamedStationTerminalWithoutSplitting() {
        var result = new TrainNodeInserter(PARAMETERS, routes()).insertTrainNodes(network(), List.of(train(1000)));
        assertEquals(3, result.branches().size());
        assertEquals("SS1.B", result.trainLoads().get(0).feedingNodeId());
        assertEquals("SS1.N", result.trainLoads().get(0).returnNodeId());
    }
    @Test public void reportsUnplaceableTrainWithoutDumpingNetworkToStderr() {
        PrintStream previous = System.err; var bytes = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(bytes)) {
            System.setErr(captured);
            try { new TrainNodeInserter(PARAMETERS, routes()).insertTrainNodes(network(), List.of(train(1200))); fail("Expected placement failure"); }
            catch (IllegalArgumentException error) {
                assertTrue(error.getMessage().contains("ad-001")); assertTrue(error.getMessage().contains("route=AD"));
                assertTrue(error.getMessage().contains("101/N")); assertTrue(error.getMessage().contains("1200.0"));
            }
        } finally { System.setErr(previous); }
        assertEquals("", bytes.toString());
    }
    @Test public void rejectsLineAssignedToBothConductorRoles() {
        var invalid = List.of(new Route("AD", List.of("L02.N.F"), List.of("L02.N.F")));
        try { new TrainNodeInserter(PARAMETERS, invalid).insertTrainNodes(network(), List.of(train(608))); fail("Expected ambiguous role failure"); }
        catch (IllegalArgumentException error) { assertTrue(error.getMessage().contains("both feeding and return")); }
    }
}
