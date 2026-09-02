package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationConnection;
import org.supply.domain.InstallationType;
import org.supply.domain.Line;
import org.supply.domain.Load;
import org.supply.domain.Node;
import org.supply.domain.PowerInstallation;
import org.supply.domain.RectifierType;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationNodeType;
import org.supply.solver.model.DiodeSubstationElement;
import org.supply.solver.model.FixedLoadElement;
import org.supply.track.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class CalculationNetworkBuilderTest {

    @Test
    public void buildBaseCreatesGridNodesAndBranches() {
        Node a = new Node("A", "1 0+0");
        Node b = new Node("B", "1 1+000");

        Line line = new Line("L1", a, b, Real.fromDouble(0.01));

        GridModel grid = new GridModel();

        grid.addNode(a);
        grid.addNode(b);

        grid.addLine(line);
        TrackTransformService transform = new FakeTrackTransformService();

        CalculationNetwork network =
                new CalculationNetworkBuilder(transform).buildBase(grid);

        assertEquals(2, network.nodes().size());
        assertEquals(1, network.branches().size());

        assertEquals("A", network.nodes().get(0).id());
        assertEquals("A", network.nodes().get(0).sourceId());
        assertEquals("1", network.nodes().get(0).sectionId());
        assertEquals("SINGLE", network.nodes().get(0).trackId());
        assertEquals(0.0, network.nodes().get(0).positionM(), 1e-9);
        assertEquals(CalculationNodeType.GRID_NODE, network.nodes().get(0).type());

        assertEquals("B", network.nodes().get(1).id());
        assertEquals(1000.0, network.nodes().get(1).positionM(), 1e-9);

        assertEquals("A", network.branches().get(0).fromNodeId());
        assertEquals("B", network.branches().get(0).toNodeId());
        assertEquals(10.0, network.branches().get(0).resistanceOhm().asDouble(), 1e-9);
    }

    private static final class FakeTrackTransformService implements TrackTransformService {

        @Override
        public ModelCoordinate toModel(String routeId, RwyCoordinate railwayCoordinate) {
            return new ModelCoordinate(
                    railwayCoordinate.getSectionId(),
                    "SINGLE",
                    railwayCoordinate.getPositionM()
            );
        }

        @Override
        public RwyCoordinate toRailway(String routeId, ModelCoordinate modelCoordinate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCoordinate pathToModel(String routeId, double pathPositionM) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RwyCoordinate pathToRailway(String routeId, double pathPositionM) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double distanceOnRoute(String routeId, RwyCoordinate from, RwyCoordinate to) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void acceptsSubstationWithExistingFeedingAndReturnNodes() {

        CalculationNetwork network =
                networkWithNodes("feed-1", "return-1");

        validateSubstationTerminals(
                network,
                "feed-1",
                "return-1"
        );
    }

    @Test
    public void rejectsSubstationWithMissingFeedingNode() {

        CalculationNetwork network =
                networkWithNodes("return-1");

        try {

            validateSubstationTerminals(
                    network,
                    "missing-feed",
                    "return-1"
            );

            fail("Expected IllegalArgumentException");

        } catch (IllegalArgumentException ex) {

            assertTrue(
                    ex.getMessage().contains("missing-feed")
            );
        }
    }

    @Test
    public void rejectsSubstationWithMissingReturnNode() {

        CalculationNetwork network =
                networkWithNodes("feed-1");

        try {

            validateSubstationTerminals(
                    network,
                    "feed-1",
                    "missing-return"
            );

            fail("Expected IllegalArgumentException");

        } catch (IllegalArgumentException ex) {

            assertTrue(
                    ex.getMessage().contains("missing-return")
            );
        }
    }

    @Test
    public void propagatesSubstationEnabledFlagToDiodeElement() {
        GridModel gridModel = new GridModel();

        Node feedingNode = new Node(
                "F1",
                "1 0+000"
        );

        Node returnNode = new Node(
                "R1",
                "1 0+000"
        );

        gridModel.addNode(feedingNode);
        gridModel.addNode(returnNode);

        PowerInstallation substation = new PowerInstallation(
                "SS1",
                InstallationType.SUBSTATION,
                false,
                Real.fromDouble(750.0),
                Real.fromDouble(0.005),
                RectifierType.DIODE
        );

        gridModel.addInstallation(substation);

        gridModel.addInstallationConnection(
                new InstallationConnection(
                        "SS1",
                        "F1",
                        ConnectionType.FEEDING
                )
        );

        gridModel.addInstallationConnection(
                new InstallationConnection(
                        "SS1",
                        "R1",
                        ConnectionType.RETURN
                )
        );

        TrackTransformService trackTransform =
                new FakeTrackTransformService();

        CalculationNetwork network =
                new CalculationNetworkBuilder(trackTransform)
                        .buildBase(gridModel);

        DiodeSubstationElement diode =
                network.elements().stream()
                        .filter(DiodeSubstationElement.class::isInstance)
                        .map(DiodeSubstationElement.class::cast)
                        .findFirst()
                        .orElseThrow();

        assertFalse(diode.enabled());
    }

    private static CalculationNetwork networkWithNodes(String... nodeIds) {
        List<CalculationNode> nodes = new ArrayList<>();

        for (String nodeId : nodeIds) {
            nodes.add(new CalculationNode(
                    nodeId,
                    null,
                    "section-1",
                    "track-1",
                    0.0,
                    CalculationNodeType.GRID_NODE
            ));
        }

        return new CalculationNetwork(nodes, List.of(), List.of(), List.of());
    }

    private static void validateSubstationTerminals(
            CalculationNetwork network,
            String feedingNodeId,
            String returnNodeId
    ) {
        Set<String> nodeIds = network.nodes().stream()
                .map(CalculationNode::id)
                .collect(Collectors.toSet());

        if (!nodeIds.contains(feedingNodeId)) {
            throw new IllegalArgumentException(
                    "Substation feeding terminal node not found: " + feedingNodeId
            );
        }

        if (!nodeIds.contains(returnNodeId)) {
            throw new IllegalArgumentException(
                    "Substation return terminal node not found: " + returnNodeId
            );
        }
    }

    @Test
    public void buildsFixedLoadElement() {
        GridModel gridModel = new GridModel();

        Node feedingNode = new Node(
                "F_FL1",
                "1 0+500"
        );

        Node returnNode = new Node(
                "R_FL1",
                "1 0+500"
        );

        gridModel.addNode(feedingNode);
        gridModel.addNode(returnNode);

        Load.FixedLoad fixedLoad = new Load.FixedLoad(
                "FL1",
                RwyCoordinateParser.parse("1 0+500"),
                1_000_000.0
        );

        gridModel.addFixedLoad(fixedLoad);

        PowerInstallation installation = new PowerInstallation(
                "FL1",
                InstallationType.FIXED_LOAD,
                true,
                Real.ZERO,
                Real.ZERO,
                null
        );

        gridModel.addInstallation(installation);

        gridModel.addInstallationConnection(
                new InstallationConnection(
                        "FL1",
                        "F_FL1",
                        ConnectionType.FEEDING
                )
        );

        gridModel.addInstallationConnection(
                new InstallationConnection(
                        "FL1",
                        "R_FL1",
                        ConnectionType.RETURN
                )
        );

        TrackTransformService trackTransform =
                new FakeTrackTransformService();

        CalculationNetwork network =
                new CalculationNetworkBuilder(trackTransform)
                        .buildBase(gridModel);

        FixedLoadElement element =
                network.elements().stream()
                        .filter(FixedLoadElement.class::isInstance)
                        .map(FixedLoadElement.class::cast)
                        .findFirst()
                        .orElseThrow();

        assertEquals("FL1", element.id());
        assertEquals("F_FL1", element.feedingNodeId());
        assertEquals("R_FL1", element.returnNodeId());
        assertEquals(1_000_000.0, element.powerW(), 1e-9);
    }
}