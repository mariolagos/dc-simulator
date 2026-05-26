package org.supply.solver.build;

import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationTrainLoad;

public final class TopologyPrinter {

    public static void print(CalculationNetwork network) {
        System.out.println("=== Nodes ===");

        for (CalculationNode node : network.nodes()) {
            System.out.println(
                    node.id()
                            + " (" + node.positionM() + ")"
            );
        }

        System.out.println("=== Branches ===");

        for (CalculationBranch branch : network.branches()) {
            System.out.println(
                    branch.fromNodeId()
                            + " --[" + branch.resistanceOhm() + "]--> "
                            + branch.toNodeId()
            );
        }

        System.out.println("=== Train loads ===");

        for (CalculationTrainLoad load : network.trainLoads()) {
            System.out.println(
                    load.feedingNodeId()
                            + " ==TRAIN("
                            + load.trainId()
                            + ")== "
                            + load.returnNodeId()
            );
        }
    }
}