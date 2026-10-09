package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.Route;
import org.supply.math.Real;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationNodeType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ElectricalRouteCheckTest {
    @Test
    public void acceptsRouteAcrossSeparateTerminalsOfSameInternalBus() {
        CalculationNetwork network = network(List.of(
                branch("f1", "LF1", "F0", "F1"),
                branch("f2", "LF2", "F2", "F3"),
                branch("r1", "LR1", "R0", "R1"),
                branch("r2", "LR2", "R2", "R3"),
                branch("if", "internal_SS1", "F1", "F2"),
                branch("ir", "internal_SS1", "R1", "R2")
        ));
        Route route = new Route("RED", List.of("LF1", "LF2"), List.of("LR1", "LR2"));
        ElectricalRouteCheck.Result result = ElectricalRouteCheck.inspect(network, List.of(route), null);
        assertTrue(result.errors().toString(), result.errors().isEmpty());
        assertTrue(result.topology().contains("internal_SS1: F1 <-> F2"));
        assertTrue(result.topology().contains("ROUTE RED"));
    }

    @Test
    public void reportsDisconnectedConsecutiveLines() {
        Route route = new Route("RED", List.of("LF1", "LF2"), List.of("LR1"));
        ElectricalRouteCheck.Result result = ElectricalRouteCheck.inspect(network(List.of(
                branch("f1", "LF1", "F0", "F1"),
                branch("f2", "LF2", "F2", "F3"),
                branch("r1", "LR1", "R0", "R1")
        )), List.of(route), null);
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("gap between LF1 and LF2")));
    }

    @Test
    public void reportsUnknownLineAndSelectedRoute() {
        Route route = new Route("RED", List.of("MISSING"), List.of("LR1"));
        ElectricalRouteCheck.Result result = ElectricalRouteCheck.inspect(network(List.of(
                branch("r1", "LR1", "R0", "R1")
        )), List.of(route), "BLUE");
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("unknown line MISSING")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("Unknown selected route ID: BLUE")));
    }

    @Test
    public void reportsFeedingReturnShortCircuitRegardlessOfNodeNames() {
        Route route = new Route("RED", List.of("LF1"), List.of("LR1"));
        ElectricalRouteCheck.Result result = ElectricalRouteCheck.inspect(network(List.of(
                branch("f1", "LF1", "F0", "R1"),
                branch("r1", "LR1", "R1", "R2")
        )), List.of(route), null);
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("electrically joined")));
    }

    @Test
    public void routeSelectionDoesNotSkipValidationOfOtherRoutes() {
        Route red = new Route("RED", List.of("LF1"), List.of("LR1"));
        Route blue = new Route("BLUE", List.of("MISSING"), List.of("LR1"));
        ElectricalRouteCheck.Result result = ElectricalRouteCheck.inspect(network(List.of(
                branch("f1", "LF1", "F0", "F1"),
                branch("r1", "LR1", "R0", "R1")
        )), List.of(red, blue), "RED");
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("BLUE FEEDING: unknown line")));
        assertFalse(result.topology().contains("ROUTE BLUE"));
        assertTrue(result.topology().contains("ROUTE RED"));
    }

    @Test
    public void acceptsObjectNamesAndInternalStationConnections() {
        Route route = new Route("AD", List.of("L01.N.F", "L02.N.F"), List.of("L01.N.R", "L02.N.R"));
        var result = ElectricalRouteCheck.inspect(network(List.of(
                branch("f1", "L01.N.F", "START.N.F", "SS1.A"),
                branch("f2", "L02.N.F", "SS1.B", "J101.N.F"),
                branch("r1", "L01.N.R", "START.N.R", "SS1.N"),
                branch("r2", "L02.N.R", "SS1.N", "J101.N.R"),
                branch("bus", "internal_SS1", "SS1.A", "SS1.B")
        )), List.of(route), null);
        assertTrue(result.errors().toString(), result.errors().isEmpty());
        assertTrue(result.topology().contains("internal_SS1: SS1.A <-> SS1.B"));
    }

    @Test
    public void reportsAmbiguousLineRolesAcrossDifferentRoutes() {
        var network = network(List.of(branch("one", "shared", "one", "two"),
                branch("two", "otherF", "three", "four"), branch("three", "otherR", "five", "six")));
        var routes = List.of(new Route("AD", List.of("shared"), List.of("otherR")),
                new Route("DA", List.of("otherF"), List.of("shared")));
        var result = ElectricalRouteCheck.inspect(network, routes, null);
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("both feeding and return: shared")));
    }

    private static CalculationBranch branch(String id, String source, String from, String to) {
        return new CalculationBranch(id, source, from, to, Real.fromDouble(0.1));
    }

    private static CalculationNetwork network(List<CalculationBranch> branches) {
        List<CalculationNode> nodes = new ArrayList<>();
        branches.stream().flatMap(branch -> java.util.stream.Stream.of(
                branch.fromNodeId(), branch.toNodeId())).distinct().forEach(id ->
                nodes.add(new CalculationNode(id, id, "1", "U", 0.0, CalculationNodeType.GRID_NODE)));
        return new CalculationNetwork(nodes, branches, List.of(), new ArrayList<>(branches));
    }
}
