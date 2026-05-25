package org.supply.solver.build;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceSystem;
import org.supply.solver.electrical.AdmittanceSystemBuilder;
import org.supply.solver.electrical.MatrixPrinter;
import org.supply.solver.model.*;

import java.util.List;

import static org.junit.Assert.*;

public final class C1SingleTimestepTopologyTest {

    @Test
    public void buildsC1SingleTimestepTopologyWithOneTrainInsertedIntoLineSegment() {

        CalculationNode a = node("substation_feed", 0.0);
        CalculationNode b = node("line_end", 1000.0);

        CalculationBranch line = new CalculationBranch(
                "line_0",
                "substation_feed-line_end",
                "substation_feed",
                "line_end",
                Real.fromDouble(1.0)
        );

        CalculationNetwork base = new CalculationNetwork(
                List.of(a, b),
                List.of(line)
        );

        CalculationTrainPosition train = new CalculationTrainPosition(
                "train_1",
                "section-C1",
                "track-1",
                400.0
        );

        CalculationNetwork result =
                new TrainNodeInserter()
                        .insertTrainNodes(base, List.of(train));

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        result,
                        "substation_feed"
                );

        MatrixPrinter.printSystem(
                "C1 passive system",
                system,
                20,
                20,
                3
        );

        assertEquals(3, result.nodes().size());
        assertEquals(2, result.branches().size());

        assertEquals("substation_feed", result.nodes().get(0).id());
        assertEquals("line_end", result.nodes().get(1).id());
        assertEquals("train_train_1", result.nodes().get(2).id());

        CalculationBranch first = result.branches().get(0);
        CalculationBranch second = result.branches().get(1);

        assertEquals("substation_feed", first.fromNodeId());
        assertEquals("train_train_1", first.toNodeId());
        assertEquals(0.4, first.resistanceOhm().asDouble(), 1e-9);

        assertEquals("train_train_1", second.fromNodeId());
        assertEquals("line_end", second.toNodeId());
        assertEquals(0.6, second.resistanceOhm().asDouble(), 1e-9);

        Real[][] g = system.conductanceMatrix();

        assertEquals(2.5, g[0][0].asDouble(), 1e-9);
        assertEquals(0.0, g[0][1].asDouble(), 1e-9);
        assertEquals(-2.5, g[0][2].asDouble(), 1e-9);

        assertEquals(0.0, g[1][0].asDouble(), 1e-9);
        assertEquals(1.6666666667, g[1][1].asDouble(), 1e-9);
        assertEquals(-1.6666666667, g[1][2].asDouble(), 1e-9);

        assertEquals(-2.5, g[2][0].asDouble(), 1e-9);
        assertEquals(-1.6666666667, g[2][1].asDouble(), 1e-9);
        assertEquals(4.1666666667, g[2][2].asDouble(), 1e-9);
    }
    private static CalculationNode node(String id, double positionM) {
        return new CalculationNode(
                id,
                null,
                "section-C1",
                "track-1",
                positionM,
                CalculationNodeType.GRID_NODE
        );
    }
}