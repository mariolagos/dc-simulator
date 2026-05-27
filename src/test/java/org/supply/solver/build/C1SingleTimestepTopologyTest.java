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

        CalculationNode a = node("F1", 0.0);
        CalculationNode b = node("F2", 1000.0);
        CalculationNode rTrain = node("R_TRAIN", 400.0);

        CalculationBranch line = new CalculationBranch(
                "line_0",
                "F1-F2",
                "F1",
                "F2",
                Real.fromDouble(1.0)
        );

        CalculationNetwork base = new CalculationNetwork(
                List.of(a, b, rTrain),
                List.of(line),
                List.of(),
                List.of(line)
        );

        CalculationTrainPosition train = new CalculationTrainPosition(
                "train_1",
                "section-C1",
                "track-1",
                400.0,
                Real.fromDouble(0.)
        );

        CalculationNetwork result =
                new TrainNodeInserter()
                        .insertTrainNodes(base, List.of(train));

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        result,
                        "F1"
                );

        MatrixPrinter.printSystem(
                "C1 passive system",
                system,
                20,
                20,
                3
        );

        assertEquals("F1", result.nodes().get(0).id());
        assertEquals("F2", result.nodes().get(1).id());
        assertEquals("R_TRAIN", result.nodes().get(2).id());
        assertEquals("train_train_1", result.nodes().get(3).id());

        assertEquals(4, result.nodes().size());
        assertEquals(2, result.branches().size());
        assertEquals(1, result.trainLoads().size());

        assertEquals("train_train_1", result.trainLoads().get(0).feedingNodeId());
        assertEquals("R_TRAIN", result.trainLoads().get(0).returnNodeId());
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