package org.supply.solver.build;

import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationConnection;
import org.supply.domain.Line;
import org.supply.domain.Node;
import org.supply.domain.PowerInstallation;
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

        addSubstationBranches(gridModel, branches);

        return new CalculationNetwork(nodes, branches, List.of());
    }

    private static List<InstallationConnection> connectionsFor(
            GridModel grid,
            PowerInstallation installation
    ) {
        List<InstallationConnection> out = new ArrayList<>();

        for (InstallationConnection c : grid.getInstallationConnections()) {
            if (Objects.equals(c.getInstallationId(), installation.getInstallationId())) {
                out.add(c);
            }
        }

        return out;
    }

    private static void addSubstationBranches(
            GridModel gridModel,
            List<CalculationBranch> branches
    ) {
        for (PowerInstallation inst : gridModel.getInstallations()) {
            if (!inst.isSubstation()) {
                continue;
            }

            InstallationConnection feeding = singleConnection(
                    gridModel,
                    inst,
                    ConnectionType.FEEDING
            );

            InstallationConnection returning = singleConnection(
                    gridModel,
                    inst,
                    ConnectionType.RETURN
            );

            if (feeding.getNodeId().equals(returning.getNodeId())) {
                throw new IllegalArgumentException(
                        "Substation " + inst.getInstallationId()
                                + " has same feeding and return node: "
                                + feeding.getNodeId()
                );
            }

            branches.add(new CalculationBranch(
                    "substation_" + inst.getInstallationId(),
                    inst.getInstallationId(),
                    feeding.getNodeId(),
                    returning.getNodeId(),
                    inst.getInternalResistanceOhm()
            ));
        }
    }

    private static InstallationConnection singleConnection(
            GridModel gridModel,
            PowerInstallation inst,
            ConnectionType type
    ) {
        InstallationConnection found = null;

        for (InstallationConnection conn : gridModel.getInstallationConnections()) {
            if (!conn.getInstallationId().equals(inst.getInstallationId())) {
                continue;
            }

            if (!conn.getConnectionType().equals(type)) {
                continue;
            }

            if (found != null) {
                throw new IllegalArgumentException(
                        "Substation " + inst.getInstallationId()
                                + " has multiple " + type + " connections"
                );
            }

            found = conn;
        }

        if (found == null) {
            throw new IllegalArgumentException(
                    "Substation " + inst.getInstallationId()
                            + " missing " + type + " connection"
            );
        }

        return found;
    }

}