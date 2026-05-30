package org.supply.solver.testsupport;

import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.ArrayList;
import java.util.List;

public final class ElectricalTestCases {

    private ElectricalTestCases() {
    }

    public static CalculationNetwork oneSubstationOneTrainLine() {
        CalculationNode fSub = node("F_SUB", 0.0);
        CalculationNode fTrain = node("F_TRAIN", 1000.0);
        CalculationNode rSub = node("R_SUB", 0.0);
        CalculationNode rTrain = node("R_TRAIN", 1000.0);

        CalculationBranch feedLine =
                branch("feed_line", "F_SUB", "F_TRAIN", 0.1);

        CalculationBranch returnLine =
                branch("return_line", "R_TRAIN", "R_SUB", 0.1);

        SubstationElement substation = new SubstationElement(
                "SS1",
                "F_SUB",
                "R_SUB",
                Real.fromDouble(780.0),
                Real.fromDouble(0.1)
        );

        List<CalculationNode> nodes = List.of(
                fSub,
                fTrain,
                rTrain,
                rSub
        );

        List<CalculationBranch> branches = List.of(
                feedLine,
                returnLine
        );

        List<ElectricalElement> elements = new ArrayList<>();
        elements.addAll(branches);
        elements.add(substation);

        return new CalculationNetwork(
                nodes,
                branches,
                List.of(),
                elements
        );
    }

    private static CalculationNode node(String id, double positionM) {
        return new CalculationNode(
                id,
                null,
                "section-1",
                "SINGLE",
                positionM,
                CalculationNodeType.GRID_NODE
        );
    }

    private static CalculationBranch branch(
            String id,
            String from,
            String to,
            double resistanceOhm
    ) {
        return new CalculationBranch(
                id,
                id,
                from,
                to,
                Real.fromDouble(resistanceOhm)
        );
    }
}