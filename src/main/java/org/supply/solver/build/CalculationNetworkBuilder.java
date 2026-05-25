package org.supply.solver.build;

import org.supply.domain.Line;
import org.supply.domain.Node;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationNodeType;
import org.supply.track.ModelCoordinate;
import org.supply.track.RwyCoordinate;
import org.supply.track.RwyCoordinateParser;
import org.supply.track.TrackTransformService;

import java.util.*;

public final class CalculationNetworkBuilder {

    private static final double MAX_CONDUCTANCE_SIEMENS = 1e9;
    private static final double MIN_RESISTANCE_OHM = 1.0 / MAX_CONDUCTANCE_SIEMENS;

    private final TrackTransformService trackTransform;

    public CalculationNetworkBuilder(TrackTransformService trackTransform) {
        this.trackTransform = Objects.requireNonNull(trackTransform, "trackTransform");
    }

    public CalculationNetwork buildBase(GridModel gridModel) {
        List<CalculationNode> nodes = new ArrayList<>();
        List<CalculationBranch> branches = new ArrayList<>();
        Map<String, ModelCoordinate> coordByNodeId = new LinkedHashMap<>();

        for (Node node : gridModel.getNodes()) {
            RwyCoordinate rwy = RwyCoordinateParser.parse(node.getPositionRwy());
            ModelCoordinate model = trackTransform.toModel(rwy.getSectionId(), rwy);

            coordByNodeId.put(node.getNodeId(), model);

            nodes.add(new CalculationNode(
                    node.getNodeId(),
                    node.getNodeId(),
                    model.getSectionId(),
                    model.getTrackId(),
                    model.getPositionM(),
                    CalculationNodeType.GRID_NODE
            ));
        }

        int branchIndex = 0;

        for (Line line : gridModel.getLines()) {
            branchIndex++;

            Node from = line.getFromNode();
            Node to = line.getToNode();

            ModelCoordinate fromModel = coordByNodeId.get(from.getNodeId());
            ModelCoordinate toModel = coordByNodeId.get(to.getNodeId());

            double lengthM = Math.abs(toModel.getPositionM() - fromModel.getPositionM());

            double rawResistanceOhm =
                    line.getResistanceOhmPerM().asDouble() * lengthM;

            Real resistanceOhm = Real.fromDouble(
                    Math.max(rawResistanceOhm, MIN_RESISTANCE_OHM)
            );

            branches.add(new CalculationBranch(
                    "line_" + branchIndex,
                    from.getNodeId() + "-" + to.getNodeId(),
                    from.getNodeId(),
                    to.getNodeId(),
                    resistanceOhm
            ));
        }

        return new CalculationNetwork(nodes, branches);
    }
}