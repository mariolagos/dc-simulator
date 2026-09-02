package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceSystem;
import org.supply.solver.electrical.AdmittanceSystemBuilder;
import org.supply.solver.electrical.MatrixPrinter;
import org.supply.solver.model.*;
import org.supply.solver.testsupport.ElectricalTestCases;

import java.util.List;

import static org.junit.Assert.*;

public final class C1SingleTimestepTopologyTest {

    @Test
    public void buildsC1SingleTimestepTopologyWithOneTrainInsertedIntoLineSegment() {

        CalculationNode f1 = node("F1", 0.0);
        CalculationNode f2 = node("F2", 1000.0);
        CalculationNode r1 = node("R1", 0.0);
        CalculationNode r2 = node("R2", 1000.0);

        CalculationBranch feedingLine = new CalculationBranch(
                "feeding_0",
                "F1-F2",
                "F1",
                "F2",
                Real.fromDouble(1.0)
        );

        CalculationBranch returnLine = new CalculationBranch(
                "return_0",
                "R1-R2",
                "R1",
                "R2",
                Real.fromDouble(1.0)
        );

        CalculationNetwork base = new CalculationNetwork(
                List.of(f1, f2, r1, r2),
                List.of(feedingLine, returnLine),
                List.of(),
                List.of(feedingLine, returnLine)
        );

        CalculationTrainPosition train = new CalculationTrainPosition(
                "train_1",
                "section-C1",
                "track-1",
                "U",
                400.0,
                Real.fromDouble(0.0)
        );

        CalculationNetwork result =
                new TrainNodeInserter(ElectricalTestCases.SYSTEM_PARAMETERS, List.of())
                        .insertTrainNodes(base, List.of(train));

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        result,
                        "R1"
                );

        MatrixPrinter.printSystem(
                "C1 passive system",
                system,
                20,
                20,
                3
        );

        assertEquals(6, result.nodes().size());
        assertEquals(4, result.branches().size());
        assertEquals(1, result.trainLoads().size());

        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_train_1_F")));

        assertTrue(result.nodes().stream()
                .anyMatch(n -> n.id().equals("train_train_1_R")));

        assertEquals(
                "train_train_1_F",
                result.trainLoads().get(0).feedingNodeId()
        );

        assertEquals(
                "train_train_1_R",
                result.trainLoads().get(0).returnNodeId()
        );
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