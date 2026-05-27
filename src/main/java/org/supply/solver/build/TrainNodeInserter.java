package org.supply.solver.build;

import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.*;

public final class TrainNodeInserter {

    private static final double EPS = 1e-9;

    public CalculationNetwork insertTrainNodes(
            CalculationNetwork baseNetwork,
            List<CalculationTrainPosition> trains
    ) {
        List<CalculationNode> nodes = new ArrayList<>(baseNetwork.nodes());
        List<CalculationBranch> outBranches = new ArrayList<>();
        List<CalculationTrainLoad> trainLoads = new ArrayList<>();
        Set<String> placedTrainIds = new HashSet<>();

        for (CalculationBranch branch : baseNetwork.branches()) {
            CalculationNode from = findNode(nodes, branch.fromNodeId());
            CalculationNode to = findNode(nodes, branch.toNodeId());

            placeTrainsAtExistingNodes(
                    nodes,
                    trains,
                    placedTrainIds,
                    trainLoads,
                    from,
                    to
            );

            List<CalculationTrainPosition> trainsOnBranch =
                    trainsInsideBranch(from, to, trains, placedTrainIds);

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

                CalculationNode returnNode = findReturnNode(nodes, train);

                trainLoads.add(new CalculationTrainLoad(
                        train.trainId(),
                        trainNode.id(),
                        returnNode.id(),
                        train.pReqW()
                ));

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

        List<ElectricalElement> elements = new ArrayList<>();
        elements.addAll(outBranches);

        for (ElectricalElement e : baseNetwork.elements()) {
            if (!(e instanceof CalculationBranch)) {
                elements.add(e);
            }
        }

        return new CalculationNetwork(nodes, outBranches, trainLoads, elements);    }

    private static void placeTrainsAtExistingNodes(
            List<CalculationNode> nodes,
            List<CalculationTrainPosition> trains,
            Set<String> placedTrainIds,
            List<CalculationTrainLoad> trainLoads,
            CalculationNode from,
            CalculationNode to
    ) {
        for (CalculationTrainPosition train : trains) {
            if (placedTrainIds.contains(train.trainId())) {
                continue;
            }

            if (!sameTrack(from, train) || !sameTrack(to, train)) {
                continue;
            }

            if (isAtNode(from, train) || isAtNode(to, train)) {
                addTrainLoadAtExistingPosition(
                        trainLoads,
                        nodes,
                        train,
                        train.positionM()
                );
                placedTrainIds.add(train.trainId());
            }
        }
    }

    private static List<CalculationTrainPosition> trainsInsideBranch(
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

            if (train.positionM() > min + EPS && train.positionM() < max - EPS) {
                out.add(train);
            }
        }

        return out;
    }

    private static boolean isAtNode(CalculationNode node, CalculationTrainPosition train) {
        return sameTrack(node, train)
                && Math.abs(node.positionM() - train.positionM()) <= EPS;
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

        if (totalLength <= EPS) {
            throw new IllegalArgumentException("Cannot split zero-length branch: " + original.id());
        }

        for (int i = 0; i < chain.size() - 1; i++) {
            CalculationNode a = chain.get(i);
            CalculationNode b = chain.get(i + 1);

            double segmentLength = Math.abs(b.positionM() - a.positionM());

            if (segmentLength <= EPS) {
                throw new IllegalArgumentException(
                        "Zero-length split branch between "
                                + a.id() + " and " + b.id()
                );
            }

            Real segmentR = Real.fromDouble(totalR * segmentLength / totalLength);

            out.add(new CalculationBranch(
                    original.id() + "_part_" + (i + 1),
                    original.sourceId(),
                    a.id(),
                    b.id(),
                    segmentR
            ));
        }
    }

    private static void addTrainLoadAtExistingPosition(
            List<CalculationTrainLoad> trainLoads,
            List<CalculationNode> nodes,
            CalculationTrainPosition train,
            double positionM
    ) {
        CalculationNode feeding = null;
        CalculationNode returning = null;

        for (CalculationNode node : nodes) {
            if (!Objects.equals(node.sectionId(), train.sectionId())) {
                continue;
            }

            if (Math.abs(node.positionM() - positionM) > EPS) {
                continue;
            }

            if (node.id().startsWith("F")) {
                feeding = node;
            } else if (node.id().startsWith("R")) {
                returning = node;
            }
        }

        if (feeding == null || returning == null) {
            throw new IllegalArgumentException(
                    "Cannot attach train " + train.trainId()
                            + " at " + train.sectionId()
                            + "/" + train.trackId()
                            + " position " + positionM
                            + ": feeding/return node pair not found"
            );
        }

        trainLoads.add(new CalculationTrainLoad(
                train.trainId(),
                feeding.id(),
                returning.id(),
                train.pReqW()
        ));
    }

    private static CalculationNode findReturnNode(
            List<CalculationNode> nodes,
            CalculationTrainPosition train
    ) {
        for (CalculationNode node : nodes) {
            if (!Objects.equals(node.sectionId(), train.sectionId())) {
                continue;
            }

            if (Math.abs(node.positionM() - train.positionM()) > EPS) {
                continue;
            }

            if (node.id().startsWith("R")) {
                return node;
            }
        }

        throw new IllegalArgumentException(
                "Return node not found for train "
                        + train.trainId()
                        + " at position "
                        + train.positionM()
        );
    }

    private static boolean sameTrack(CalculationNode node, CalculationTrainPosition train) {
        return Objects.equals(node.sectionId(), train.sectionId())
                && compatibleTrack(node.trackId(), train.trackId());
    }

    private static boolean compatibleTrack(String nodeTrackId, String trainTrackId) {
        if (nodeTrackId == null || nodeTrackId.isBlank()) {
            return true;
        }
        if (trainTrackId == null || trainTrackId.isBlank()) {
            return true;
        }
        return Objects.equals(nodeTrackId, trainTrackId);
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