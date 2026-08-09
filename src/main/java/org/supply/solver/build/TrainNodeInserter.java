package org.supply.solver.build;

import org.supply.domain.SystemParameters;
import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.*;


public final class TrainNodeInserter {

    private static final double EPS = 1e-9;

    private final SystemParameters systemParameters;

    public TrainNodeInserter(SystemParameters systemParameters) {
        this.systemParameters = Objects.requireNonNull(
                systemParameters,
                "systemParameters"
        );
    }
    public CalculationNetwork insertTrainNodes(
            CalculationNetwork baseNetwork,
            List<CalculationTrainPosition> trains
    ) {
        List<CalculationNode> nodes = new ArrayList<>(baseNetwork.nodes());
        List<CalculationBranch> outBranches = new ArrayList<>();
        Map<String, String> feedingNodeByTrain = new LinkedHashMap<>();
        Map<String, String> returnNodeByTrain = new LinkedHashMap<>();

        for (CalculationBranch branch : baseNetwork.branches()) {
            CalculationNode from = findNode(nodes, branch.fromNodeId());
            CalculationNode to = findNode(nodes, branch.toNodeId());

            boolean feedingBranch = from.id().startsWith("F") && to.id().startsWith("F");
            boolean returnBranch = from.id().startsWith("R") && to.id().startsWith("R");

            if (!feedingBranch && !returnBranch) {
                outBranches.add(branch);
                continue;
            }

            Map<String, String> placedForBranch =
                    feedingBranch ? feedingNodeByTrain : returnNodeByTrain;

            for (CalculationTrainPosition train : trains) {
                if (placedForBranch.containsKey(train.trainId())) {
                    continue;
                }

                if (!sameTrack(from, train) || !sameTrack(to, train)) {
                    continue;
                }

                if (isAtNode(from, train)) {
                    placedForBranch.put(train.trainId(), from.id());
                } else if (isAtNode(to, train)) {
                    placedForBranch.put(train.trainId(), to.id());
                }
            }

            List<CalculationTrainPosition> trainsOnBranch =
                    trainsInsideBranch(from, to, trains, placedForBranch.keySet());

            if (trainsOnBranch.isEmpty()) {
                outBranches.add(branch);
                continue;
            }

            trainsOnBranch.sort(
                    Comparator
                            .comparingDouble(CalculationTrainPosition::positionM)
                            .thenComparing(CalculationTrainPosition::trainId)
            );

            if (from.positionM() > to.positionM()) {
                Collections.reverse(trainsOnBranch);
            }

            List<CalculationNode> chain = new ArrayList<>();
            chain.add(from);

            for (CalculationTrainPosition train : trainsOnBranch) {
                String suffix = feedingBranch ? "_F" : "_R";

                CalculationNode trainNode = new CalculationNode(
                        "train_" + train.trainId() + suffix,
                        train.trainId(),
                        train.sectionId(),
                        train.trackId(),
                        train.positionM(),
                        CalculationNodeType.TRAIN_NODE
                );

                nodes.add(trainNode);
                chain.add(trainNode);
                placedForBranch.put(train.trainId(), trainNode.id());
            }

            chain.add(to);
            addSplitBranches(outBranches, branch, chain);
        }

        List<CalculationTrainLoad> trainLoads = new ArrayList<>();

        for (CalculationTrainPosition train : trains) {
            String feedingNodeId = feedingNodeByTrain.get(train.trainId());
            String returnNodeId = returnNodeByTrain.get(train.trainId());

            if (feedingNodeId == null || returnNodeId == null) {
                throw new IllegalArgumentException(
                        "Could not connect train " + train.trainId()
                                + " at " + train.sectionId()
                                + "/" + train.trackId()
                                + " position " + train.positionM()
                                + ": feedingNode=" + feedingNodeId
                                + ", returnNode=" + returnNodeId
                );
            }

            trainLoads.add(new CalculationTrainLoad(
                    train.trainId(),
                    feedingNodeId,
                    returnNodeId,
                    train.pReqW()
            ));
        }

        List<ElectricalElement> elements = new ArrayList<>();
        elements.addAll(outBranches);

        for (ElectricalElement e : baseNetwork.elements()) {
            if (!(e instanceof CalculationBranch)) {
                elements.add(e);
            }
        }

        for (CalculationTrainLoad load : trainLoads) {
            elements.add(new TrainLoadElement(
                    load.feedingNodeId(),
                    load.returnNodeId(),
                    load.pReqW().asDouble(),
                    systemParameters.uNominalV(),
                    systemParameters
            ));
        }

        return new CalculationNetwork(nodes, outBranches, trainLoads, elements);    }



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