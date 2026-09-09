package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.model.*;
import org.supply.solver.testsupport.ElectricalTestCases;

import java.util.List;

import static org.junit.Assert.*;

public final class TrainNodeInserterTest {

    @Test
    public void insertsTwoTrainNodesAndSplitsBranchDeterministically() {
        CalculationNetwork base = baseNetwork();

        CalculationTrainPosition train2 = train("T2", 700.0);
        CalculationTrainPosition train1 = train("T1", 300.0);

        CalculationNetwork result =
                new TrainNodeInserter(new SystemParameters(750, 500, 600, 900, 6000, 10000000, 3600000), List.of()).insertTrainNodes(base, List.of(train2, train1));

        assertEquals(8, result.nodes().size());
        assertEquals(6, result.branches().size());
        assertEquals(2, result.trainLoads().size());

        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_T1_F")));
        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_T1_R")));
        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_T2_F")));
        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_T2_R")));

        assertBranch(result.branches().get(0), "F_A", "train_T1_F", 0.3);
        assertBranch(result.branches().get(1), "train_T1_F", "train_T2_F", 0.4);
        assertBranch(result.branches().get(2), "train_T2_F", "F_B", 0.3);
        assertBranch(result.branches().get(3), "R_A", "train_T1_R", 0.3);
        assertBranch(result.branches().get(4), "train_T1_R", "train_T2_R", 0.4);
        assertBranch(result.branches().get(5), "train_T2_R", "R_B", 0.3);
    }

    @Test
    public void rejectsTrainThatCannotBePlacedOnAnyBranch() {
        CalculationNetwork base = baseNetwork();

        try {
            new TrainNodeInserter(new SystemParameters(750, 500, 600, 900, 6000, 10000000, 3600000), List.of()).insertTrainNodes(base, List.of(train("T1", 1200.0)));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Could not connect train T1"));
        }
    }

    @Test
    public void treatsTrainExactlyAtExistingNodeAsPlacedWithoutChangingTopology() {
        CalculationNetwork base = baseNetwork();

        CalculationNetwork result =
                new TrainNodeInserter(new SystemParameters(750, 500, 600, 900, 6000, 10000000, 3600000), List.of()).insertTrainNodes(base, List.of(train("T1", 0.0)));

        assertEquals(4, result.nodes().size());
        assertEquals(2, result.branches().size());
        assertEquals(1, result.trainLoads().size());

        CalculationTrainLoad load = result.trainLoads().get(0);
        assertEquals("T1", load.trainId());
        assertEquals("F_A", load.feedingNodeId());
        assertEquals("R_A", load.returnNodeId());
        assertBranch(result.branches().get(0), "F_A", "F_B", 1.0);

//        CalculationTrainLoad load = result.trainLoads().get(0);
//        assertEquals("T1", load.trainId());
//        assertEquals("F_A", load.feedingNodeId());
//        assertEquals("R_0", load.returnNodeId());
    }

    @Test
    public void producesIdenticalTopologyForDifferentTrainInputOrder() {
        CalculationNetwork base = baseNetwork();

        CalculationNetwork result1 =
                new TrainNodeInserter(new SystemParameters(750, 500, 600, 900, 6000, 10000000, 3600000), List.of()).insertTrainNodes(base, List.of(train("T1", 300.0), train("T2", 700.0)));

        CalculationNetwork result2 =
                new TrainNodeInserter(new SystemParameters(750, 500, 600, 900, 6000, 10000000, 3600000), List.of()).insertTrainNodes(base, List.of(train("T2", 700.0), train("T1", 300.0)));

        assertSameTopology(result1, result2);
    }

    @Test
    public void producesIdenticalTopologyAcrossRepeatedRuns() {
        CalculationNetwork base = baseNetwork();

        CalculationNetwork result1 =
                new TrainNodeInserter(ElectricalTestCases.SYSTEM_PARAMETERS, List.of())
                        .insertTrainNodes(base, List.of(train("T1", 300.0), train("T2", 700.0)));

        CalculationNetwork result2 =
                new TrainNodeInserter(ElectricalTestCases.SYSTEM_PARAMETERS, List.of())
                        .insertTrainNodes(base, List.of(train("T1", 300.0), train("T2", 700.0)));

        assertSameTopology(result1, result2);
    }

    private static CalculationNetwork baseNetwork() {
        CalculationNode fA = node("F_A", 0.0);
        CalculationNode fB = node("F_B", 1000.0);
        CalculationNode rA = node("R_A", 0.0);
        CalculationNode rB = node("R_B", 1000.0);

        CalculationBranch feedingBranch = new CalculationBranch(
                "feeding-1",
                "source-1",
                "F_A",
                "F_B",
                Real.fromDouble(1.0)
        );

        CalculationBranch returnBranch = new CalculationBranch(
                "return-1",
                "source-1",
                "R_A",
                "R_B",
                Real.fromDouble(1.0)
        );

        return new CalculationNetwork(
                List.of(fA, fB, rA, rB),
                List.of(feedingBranch, returnBranch),
                List.of(),
                List.of(feedingBranch, returnBranch)
        );
    }

    private static CalculationTrainPosition train(String id, double positionM) {
        return new CalculationTrainPosition(
                id,
                "section-1",
                "track-1",
                "U",
                positionM,
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