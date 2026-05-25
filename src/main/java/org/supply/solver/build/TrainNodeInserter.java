package org.supply.solver.build;

import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.*;

public final class TrainNodeInserter {

    private static final double EPS = 1e-3;

    public CalculationNetwork insertTrainNodes(
            CalculationNetwork baseNetwork,
            List<CalculationTrainPosition> trains
    ) {
        List<CalculationNode> nodes = new ArrayList<>(baseNetwork.nodes());
        List<CalculationBranch> outBranches = new ArrayList<>();
        Set<String> placedTrainIds = new HashSet<>();

        markTrainsAtExistingNodes(baseNetwork.nodes(), trains, placedTrainIds);

        for (CalculationBranch branch : baseNetwork.branches()) {
            CalculationNode from = findNode(nodes, branch.fromNodeId());
            CalculationNode to = findNode(nodes, branch.toNodeId());

            List<CalculationTrainPosition> trainsOnBranch =
                    trainsOnBranch(from, to, trains, placedTrainIds);

            if (trainsOnBranch.isEmpty()) {
                outBranches.add(branch);
                continue;
            }

            trainsOnBranch.sort(
                    Comparator
                            .comparingDouble(CalculationTrainPosition::positionM)
                            .thenComparing(CalculationTrainPosition::trainId)
            );

            List<CalculationNode> chain = new ArrayList<>();
            chain.add(from);

            for (CalculationTrainPosition train : trainsOnBranch) {
                CalculationNode trainNode = new CalculationNode(
                        "train_" + train.trainId(),
                        train.trainId(),
                        train.sectionId(),
                        train.trackId(),
                        train.positionM(),
                        CalculationNodeType.TRAIN_NODE
                );

                nodes.add(trainNode);
                chain.add(trainNode);
                placedTrainIds.add(train.trainId());
            }

            chain.add(to);

            addSplitBranches(outBranches, branch, chain);
        }

        for (CalculationTrainPosition train : trains) {
            if (!placedTrainIds.contains(train.trainId())) {
                throw new IllegalArgumentException(
                        "Could not place train " + train.trainId()
                                + " at " + train.sectionId()
                                + "/" + train.trackId()
                                + " position " + train.positionM()
                );
            }
        }

        return new CalculationNetwork(nodes, outBranches);
    }

    private static void markTrainsAtExistingNodes(
            List<CalculationNode> nodes,
            List<CalculationTrainPosition> trains,
            Set<String> placedTrainIds
    ) {
        for (CalculationTrainPosition train : trains) {
            for (CalculationNode node : nodes) {
                if (sameTrack(node, train)
                        && Math.abs(node.positionM() - train.positionM()) <= EPS) {
                    placedTrainIds.add(train.trainId());
                    break;
                }
            }
        }
    }

    private static List<CalculationTrainPosition> trainsOnBranch(
            CalculationNode from,
            CalculationNode to,
            List<CalculationTrainPosition> trains,
            Set<String> placedTrainIds
    ) {

        List<CalculationTrainPosition> out = new ArrayList<>();

        double min = Math.min(from.positionM(), to.positionM());
        double max = Math.max(from.positionM(), to.positionM());

        for (CalculationTrainPosition train : trains) {
            if (placedTrainIds.contains(train.trainId())) {
                continue;
            }

            if (!sameTrack(from, train) || !sameTrack(to, train)) {
                continue;
            }

            if (train.positionM() > min && train.positionM() < max) {
                out.add(train);
            }
        }

        return out;
    }

    private static void addSplitBranches(
            List<CalculationBranch> out,
            CalculationBranch original,
            List<CalculationNode> chain
    ) {
        CalculationNode first = chain.get(0);
        CalculationNode last = chain.get(chain.size() - 1);

        double totalLength = Math.abs(last.positionM() - first.positionM());
        double totalR = original.resistanceOhm().asDouble();

        if (totalLength <= 0.0) {
            throw new IllegalArgumentException("Cannot split zero-length branch: " + original.id());
        }

        for (int i = 0; i < chain.size() - 1; i++) {
            CalculationNode a = chain.get(i);
            CalculationNode b = chain.get(i + 1);

            double segmentLength = Math.abs(b.positionM() - a.positionM());
            Real segmentR = Real.fromDouble(totalR * segmentLength / totalLength);

            if (segmentLength <= 0.0) {
                throw new IllegalArgumentException(
                        "Zero-length split branch between "
                                + a.id() + " and " + b.id()
                );
            }

            out.add(new CalculationBranch(
                    original.id() + "_part_" + (i + 1),
                    original.sourceId(),
                    a.id(),
                    b.id(),
                    segmentR
            ));
        }
    }

    private static boolean sameTrack(CalculationNode node, CalculationTrainPosition train) {
        return Objects.equals(node.sectionId(), train.sectionId())
                && Objects.equals(node.trackId(), train.trackId());
    }

    private static CalculationNode findNode(List<CalculationNode> nodes, String id) {
        for (CalculationNode node : nodes) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Calculation node not found: " + id);
    }
}