package org.supply.solver.build;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.List;

import static org.junit.Assert.*;

public final class TrainNodeInserterTest {

    @Test
    public void insertsTwoTrainNodesAndSplitsBranchDeterministically() {

        CalculationNode a = new CalculationNode(
                "A",
                null,
                "section-1",
                "track-1",
                0.0,
                CalculationNodeType.GRID_NODE
        );

        CalculationNode b = new CalculationNode(
                "B",
                null,
                "section-1",
                "track-1",
                1000.0,
                CalculationNodeType.GRID_NODE
        );

        CalculationBranch branch = new CalculationBranch(
                "branch-1",
                "source-1",
                "A",
                "B",
                Real.fromDouble(1.0)
        );

        CalculationNetwork base = new CalculationNetwork(
                List.of(a, b),
                List.of(branch)
        );

        // Intentionally reversed input order
        CalculationTrainPosition train2 = new CalculationTrainPosition(
                "T2",
                "section-1",
                "track-1",
                700.0
        );

        CalculationTrainPosition train1 = new CalculationTrainPosition(
                "T1",
                "section-1",
                "track-1",
                300.0
        );

        CalculationNetwork result = new TrainNodeInserter()
                .insertTrainNodes(base, List.of(train2, train1));

        assertEquals(4, result.nodes().size());
        assertEquals(3, result.branches().size());

        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_T1")));

        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_T2")));

        CalculationBranch b1 = result.branches().get(0);
        CalculationBranch b2 = result.branches().get(1);
        CalculationBranch b3 = result.branches().get(2);

        assertEquals("A", b1.fromNodeId());
        assertEquals("train_T1", b1.toNodeId());
        assertEquals(0.3, b1.resistanceOhm().asDouble(), 1e-9);

        assertEquals("train_T1", b2.fromNodeId());
        assertEquals("train_T2", b2.toNodeId());
        assertEquals(0.4, b2.resistanceOhm().asDouble(), 1e-9);

        assertEquals("train_T2", b3.fromNodeId());
        assertEquals("B", b3.toNodeId());
        assertEquals(0.3, b3.resistanceOhm().asDouble(), 1e-9);
    }

    @Test
    public void rejectsTrainThatCannotBePlacedOnAnyBranch() {

        CalculationNode a = new CalculationNode(
                "A",
                null,
                "section-1",
                "track-1",
                0.0,
                CalculationNodeType.GRID_NODE
        );

        CalculationNode b = new CalculationNode(
                "B",
                null,
                "section-1",
                "track-1",
                1000.0,
                CalculationNodeType.GRID_NODE
        );

        CalculationBranch branch = new CalculationBranch(
                "branch-1",
                "source-1",
                "A",
                "B",
                Real.fromDouble(1.0)
        );

        CalculationNetwork base = new CalculationNetwork(
                List.of(a, b),
                List.of(branch)
        );

        CalculationTrainPosition train = new CalculationTrainPosition(
                "T1",
                "section-1",
                "track-1",
                1200.0
        );

        try {
            new TrainNodeInserter()
                    .insertTrainNodes(base, List.of(train));

            fail("Expected IllegalArgumentException");

        } catch (IllegalArgumentException ex) {

            assertTrue(
                    ex.getMessage().contains("Could not place train T1")
            );
        }
    }
}