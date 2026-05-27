package org.supply.solver.build;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.List;

import static org.junit.Assert.*;

public final class TrainNodeInserterTest {

    @Test
    public void insertsTwoTrainNodesAndSplitsBranchDeterministically() {
        CalculationNetwork base = baseNetworkWithReturnNodesAt(300.0, 700.0);

        CalculationTrainPosition train2 = train("T2", 700.0);
        CalculationTrainPosition train1 = train("T1", 300.0);

        CalculationNetwork result =
                new TrainNodeInserter().insertTrainNodes(base, List.of(train2, train1));

        assertEquals(6, result.nodes().size());
        assertEquals(3, result.branches().size());
        assertEquals(2, result.trainLoads().size());

        assertTrue(result.nodes().stream().anyMatch(n -> n.id().equals("train_T1")));
        assertTrue(result.nodes().stream().anyMatch(n -> n.id().equals("train_T2")));

        assertBranch(result.branches().get(0), "F_A", "train_T1", 0.3);
        assertBranch(result.branches().get(1), "train_T1", "train_T2", 0.4);
        assertBranch(result.branches().get(2), "train_T2", "F_B", 0.3);
    }

    @Test
    public void rejectsTrainThatCannotBePlacedOnAnyBranch() {
        CalculationNetwork base = baseNetworkWithReturnNodesAt();

        try {
            new TrainNodeInserter().insertTrainNodes(base, List.of(train("T1", 1200.0)));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Could not place train T1"));
        }
    }

    @Test
    public void treatsTrainExactlyAtExistingNodeAsPlacedWithoutChangingTopology() {
        CalculationNetwork base = baseNetworkWithReturnNodesAt(0.0);

        CalculationNetwork result =
                new TrainNodeInserter().insertTrainNodes(base, List.of(train("T1", 0.0)));

        assertEquals(base.nodes().size(), result.nodes().size());
        assertEquals(1, result.branches().size());
        assertEquals(1, result.trainLoads().size());

        assertBranch(result.branches().get(0), "F_A", "F_B", 1.0);

        CalculationTrainLoad load = result.trainLoads().get(0);
        assertEquals("T1", load.trainId());
        assertEquals("F_A", load.feedingNodeId());
        assertEquals("R_0", load.returnNodeId());
    }

    @Test
    public void producesIdenticalTopologyForDifferentTrainInputOrder() {
        CalculationNetwork base = baseNetworkWithReturnNodesAt(300.0, 700.0);

        CalculationNetwork result1 =
                new TrainNodeInserter().insertTrainNodes(base, List.of(train("T1", 300.0), train("T2", 700.0)));

        CalculationNetwork result2 =
                new TrainNodeInserter().insertTrainNodes(base, List.of(train("T2", 700.0), train("T1", 300.0)));

        assertSameTopology(result1, result2);
    }

    @Test
    public void producesIdenticalTopologyAcrossRepeatedRuns() {
        CalculationNetwork base = baseNetworkWithReturnNodesAt(300.0, 700.0);

        CalculationNetwork result1 =
                new TrainNodeInserter().insertTrainNodes(base, List.of(train("T1", 300.0), train("T2", 700.0)));

        CalculationNetwork result2 =
                new TrainNodeInserter().insertTrainNodes(base, List.of(train("T1", 300.0), train("T2", 700.0)));

        assertSameTopology(result1, result2);
    }

    private static CalculationNetwork baseNetworkWithReturnNodesAt(double... returnPositions) {
        CalculationNode fA = node("F_A", 0.0);
        CalculationNode fB = node("F_B", 1000.0);

        CalculationBranch branch = new CalculationBranch(
                "branch-1",
                "source-1",
                "F_A",
                "F_B",
                Real.fromDouble(1.0)
        );

        java.util.ArrayList<CalculationNode> nodes = new java.util.ArrayList<>();
        nodes.add(fA);
        nodes.add(fB);

        for (double position : returnPositions) {
            nodes.add(node("R_" + Math.round(position), position));
        }

        return new CalculationNetwork(
                nodes,
                List.of(branch),
                List.of(),
                List.of(branch)
        );
    }

    private static CalculationTrainPosition train(String id, double positionM) {
        return new CalculationTrainPosition(
                id,
                "section-1",
                "track-1",
                positionM,
                Real.fromDouble(0.0)
        );
    }

    private static CalculationNode node(String id, double positionM) {
        return new CalculationNode(
                id,
                null,
                "section-1",
                "track-1",
                positionM,
                CalculationNodeType.GRID_NODE
        );
    }

    private static void assertBranch(
            CalculationBranch branch,
            String from,
            String to,
            double resistanceOhm
    ) {
        assertEquals(from, branch.fromNodeId());
        assertEquals(to, branch.toNodeId());
        assertEquals(resistanceOhm, branch.resistanceOhm().asDouble(), 1e-9);
    }

    private static void assertSameTopology(
            CalculationNetwork a,
            CalculationNetwork b
    ) {
        assertEquals(a.nodes().size(), b.nodes().size());
        assertEquals(a.branches().size(), b.branches().size());
        assertEquals(a.trainLoads().size(), b.trainLoads().size());

        for (int i = 0; i < a.nodes().size(); i++) {
            assertEquals(a.nodes().get(i).id(), b.nodes().get(i).id());
            assertEquals(a.nodes().get(i).positionM(), b.nodes().get(i).positionM(), 1e-9);
        }

        for (int i = 0; i < a.branches().size(); i++) {
            assertEquals(a.branches().get(i).id(), b.branches().get(i).id());
            assertEquals(a.branches().get(i).fromNodeId(), b.branches().get(i).fromNodeId());
            assertEquals(a.branches().get(i).toNodeId(), b.branches().get(i).toNodeId());
            assertEquals(
                    a.branches().get(i).resistanceOhm().asDouble(),
                    b.branches().get(i).resistanceOhm().asDouble(),
                    1e-9
            );
        }
    }
}