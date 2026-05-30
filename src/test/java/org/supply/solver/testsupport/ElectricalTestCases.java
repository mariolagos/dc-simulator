package org.supply.solver.testsupport;

import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.ArrayList;
import java.util.List;

public final class ElectricalTestCases {

    public static final double R_INTERNAL_OHM = 0.1;
    public static final double R_FEED_OHM = 0.1;
    public static final double R_RETURN_OHM = 0.1;
    public static final double U_NOMINAL_V = 750.0;
    public static final double U_MIN_V = 600.0;
    public static final double U_MAX_V = 900.0;
    public static final double U_CUTOFF_V = 950.0;
    public static final double I_TRAIN_MAX_A = 7000.0;

    public static final double TEST_CURRENT_A = 500.0;
    public static final double TEST_POWER_W =
            U_NOMINAL_V * TEST_CURRENT_A;

    public static final double EXPECTED_TRACTION_V =
            U_NOMINAL_V
                    - TEST_CURRENT_A
                    * (R_INTERNAL_OHM + R_FEED_OHM + R_RETURN_OHM);

    public static final double EXPECTED_REGEN_V =
            U_NOMINAL_V
                    + TEST_CURRENT_A
                    * (R_INTERNAL_OHM + R_FEED_OHM + R_RETURN_OHM);

    private ElectricalTestCases() {
    }

    public static CalculationNetwork oneSubstationOneTrainLine() {
        return oneSubstationOneTrainLineWithTrainPower(0.0);
    }

    public static CalculationNetwork oneSubstationOneTrainLineWithTrainPower(double pReqW) {
        CalculationNode fSub = node("F_SUB", 0.0);
        CalculationNode fTrain = node("F_TRAIN", 1000.0);
        CalculationNode rTrain = node("R_TRAIN", 1000.0);
        CalculationNode rSub = node("R_SUB", 0.0);

        CalculationBranch feedLine =
                branch("feed_line", "F_SUB", "F_TRAIN", R_FEED_OHM);

        CalculationBranch returnLine =
                branch("return_line", "R_TRAIN", "R_SUB", R_RETURN_OHM);

        DiodeSubstationElement substation = new DiodeSubstationElement(
                "SS1",
                "F_SUB",
                "R_SUB",
                Real.fromDouble(U_NOMINAL_V),
                Real.fromDouble(R_INTERNAL_OHM)
        );

        List<CalculationNode> nodes = List.of(fSub, fTrain, rTrain, rSub);
        List<CalculationBranch> branches = List.of(feedLine, returnLine);

        List<ElectricalElement> elements = new ArrayList<>();
        elements.addAll(branches);
        elements.add(substation);

        if (pReqW != 0.0) {
            elements.add(new TrainLoadElement(
                    "F_TRAIN",
                    "R_TRAIN",
                    pReqW,
                    U_NOMINAL_V
            ));
        }

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