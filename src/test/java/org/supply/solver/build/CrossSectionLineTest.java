package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.*;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.util.*;
import static org.junit.Assert.*;

public class CrossSectionLineTest {
    private static RwyCoordinate c(String section, int position, String track) {
        return new RwyCoordinate(section, RwyCoordinate.formatKmShort(position), position, track);
    }
    private static TrackSection section(String id, int railwayLength, int modelLength) {
        return new TrackSection(id, List.of(new RouteSegment(1,
                c(id, 0, null), c(id, railwayLength, null), 0, modelLength)));
    }
    private static LoadedTrackModel track(List<TrackJunction> junctions) {
        return new LoadedTrackModel(Map.of("A", section("A", 1000, 1200),
                "B", section("B", 1000, 1000), "C", section("C", 1000, 1000)), junctions, List.of());
    }
    private static List<TrackJunction> junctions() {
        return List.of(new TrackJunction(c("A", 0, "N"), c("B", 1000, "N")));
    }
    private static CalculationNetwork base(boolean reverse) {
        GridModel grid = new GridModel();
        for (String role : List.of("F", "R")) {
            Node a = new Node(role + "_A", "A 0+500 N");
            Node b = new Node(role + "_B", "B 0+800 N");
            grid.addNode(a); grid.addNode(b);
            grid.addLine(new Line(role + "_line", reverse ? b : a, reverse ? a : b, Real.fromDouble(.001)));
        }
        return new CalculationNetworkBuilder(new DefaultTrackTransformService(track(junctions()))).buildBase(grid);
    }
    private static CalculationTrainPosition train(String id, String section, String track, double position) {
        return new CalculationTrainPosition(id, section, track, "route", 0, position, Real.fromDouble(1000));
    }
    private static CalculationNetwork insert(CalculationNetwork base, List<CalculationTrainPosition> trains) {
        return new TrainNodeInserter(new SystemParameters(750, 500, 600, 900, 6000, 10000000, 3600000),
                List.of(new Route("route", List.of("F_line"), List.of("R_line"))))
                .insertTrainNodes(base, trains);
    }
    private static double total(CalculationNetwork network, String source) {
        return network.branches().stream().filter(b -> b.sourceId().equals(source))
                .mapToDouble(b -> b.resistanceOhm().asDouble()).sum();
    }
    @Test public void placesTrainsOnBothSidesAndPreservesResistance() {
        CalculationNetwork base = base(false);
        // A: 500 railway metres correspond to 600 physical metres; B contributes 200.
        assertEquals(.800000001, total(base, "F_line"), 1e-12);
        CalculationNetwork result = insert(base, List.of(train("one", "A", null, 300),
                train("two", "B", "N", 900)));
        assertEquals(2, result.trainLoads().size());
        assertEquals(total(base, "F_line"), total(result, "F_line"), 1e-12);
        assertEquals(total(base, "R_line"), total(result, "R_line"), 1e-12);
        List<Double> resistances = result.branches().stream().filter(b -> b.sourceId().equals("F_line"))
                .map(b -> b.resistanceOhm().asDouble()).toList();
        assertEquals(List.of(.3, .3, 1e-9, .1, .1), resistances);
    }
    @Test public void rendersGeneratedSectionConnectorsWithoutSourceCoordinates() throws Exception {
        for (boolean reverse : List.of(false, true)) {
            CalculationNetwork network = base(reverse);
            List<CalculationNode> nodes = List.copyOf(network.nodes());
            List<CalculationBranch> branches = List.copyOf(network.branches());
            Route route = new Route("route", List.of("F_line"), List.of("R_line"));
            String svg = RouteSchematic.svg(network, route, Map.of("F_line", .001, "R_line", .001));
            var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(svg)));
            assertEquals(4, doc.getElementsByTagName("line").getLength());
            assertTrue(svg.contains("junction connectors omitted: 2"));
            assertEquals(nodes, network.nodes());
            assertEquals(branches, network.branches());
            String combined = RouteSchematic.combinedSvg(network, List.of(route,
                            new Route("reverse", route.feedingLineIds(), route.returnLineIds())),
                    Map.of("F_line", .001, "R_line", .001));
            var combinedDoc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(combined)));
            assertEquals(8, combinedDoc.getElementsByTagName("line").getLength());
        }
    }
    @Test public void rendersZeroLengthSectionLegAtBoundary() throws Exception {
        GridModel grid = new GridModel();
        Node a = new Node("F_A", "A 0+500 N"), b = new Node("F_B", "B 1+000 N");
        grid.addNode(a); grid.addNode(b);
        grid.addLine(new Line("F_line", a, b, Real.fromDouble(.001)));
        CalculationNetwork network = new CalculationNetworkBuilder(
                new DefaultTrackTransformService(track(junctions()))).buildBase(grid);
        assertEquals(2, network.branches().stream().filter(CalculationBranch::schematicConnector).count());
        String svg = RouteSchematic.svg(network, new Route("route", List.of("F_line"), List.of()),
                Map.of("F_line", .001));
        var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(svg)));
        assertEquals(1, doc.getElementsByTagName("line").getLength());
    }
    @Test public void keepsOrdinaryCoincidentLineDiagnostic() {
        CalculationNode a = new CalculationNode("a", "a", "A", "N", 0, CalculationNodeType.GRID_NODE);
        CalculationNode b = new CalculationNode("b", "b", "A", "N", 0, CalculationNodeType.GRID_NODE);
        CalculationBranch line = new CalculationBranch("line", "line", "a", "b", Real.fromDouble(.1));
        CalculationNetwork network = new CalculationNetwork(List.of(a,b), List.of(line), List.of(), List.of(line));
        try { RouteSchematic.svg(network, new Route("route", List.of("line"), List.of()), Map.of("line", .001));
            fail("Expected coincident ordinary line diagnostic"); }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains("coincident")); }
    }
    @Test public void placesInteriorTrainsOnReversedLine() {
        CalculationNetwork base = base(true);
        CalculationNetwork result = insert(base, List.of(train("one", "A", "N", 300),
                train("two", "B", "N", 900)));
        assertEquals(2, result.trainLoads().size());
        assertEquals(total(base, "F_line"), total(result, "F_line"), 1e-12);
        assertEquals(List.of(.1, .1, 1e-9, .3, .3), result.branches().stream()
                .filter(b -> b.sourceId().equals("F_line")).map(b -> b.resistanceOhm().asDouble()).toList());
    }
    @Test public void leavesSameSectionCrossTrackConnectionsUnchanged() {
        TrackLinePath path = new TrackLinePath(track(junctions()));
        assertEquals(2, path.between(new ModelCoordinate("A", "N", 500),
                new ModelCoordinate("A", "S", 500)).size());
    }
    @Test public void supportsReverseLineAndBoundaryPositions() {
        CalculationNetwork base = base(true);
        CalculationNetwork result = insert(base, List.of(train("one", "A", "N", 0),
                train("two", "B", "N", 1000)));
        assertEquals(2, result.trainLoads().size());
        assertEquals(base.branches().size(), result.branches().size());
        assertEquals(total(base, "F_line"), total(result, "F_line"), 1e-12);
    }
    @Test public void rejectsWrongTrackAndOutsideLine() {
        for (CalculationTrainPosition train : List.of(train("wrong", "A", "S", 300),
                train("outside", "A", "N", 700))) {
            try { insert(base(false), List.of(train)); fail("Expected placement rejection"); }
            catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains("Could not connect train")); }
        }
    }
    @Test public void resolvesThreeSectionsAndRejectsMissingOrAmbiguousPath() {
        List<TrackJunction> js = new ArrayList<>(junctions());
        js.add(new TrackJunction(c("B", 0, "N"), c("C", 1000, "N")));
        ModelCoordinate a = new ModelCoordinate("A", "N", 600), c = new ModelCoordinate("C", "N", 800);
        assertEquals(6, new TrackLinePath(track(js)).between(a, c).size());
        try { new TrackLinePath(track(List.of())).between(a, c); fail("Expected missing path"); }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains("No track junction path")); }
        js.add(new TrackJunction(c("A", 0, "N"), c("C", 1000, "N")));
        try { new TrackLinePath(track(js)).between(a, c); fail("Expected ambiguous path"); }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains("Ambiguous")); }
    }
}
