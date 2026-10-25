package org.supply.solver.build;

import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationConnection;
import org.supply.domain.Line;
import org.supply.domain.Load;
import org.supply.domain.Node;
import org.supply.domain.PowerInstallation;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.CalculationNodeType;
import org.supply.solver.model.ElectricalElement;
import org.supply.solver.model.FixedLoadElement;
import org.supply.solver.model.ThyristorSubstationElement;
import org.supply.solver.model.DiodeSubstationElement;
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
            RwyCoordinate rwy =
                    RwyCoordinateParser.parse(
                            node.getPositionRwy()
                    );

            ModelCoordinate model =
                    trackTransform.toModel(
                            rwy.getSectionId(),
                            rwy
                    );

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

            List<ModelCoordinate> path;
            try {
                path = trackTransform.linePath(fromModel, toModel);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot resolve track path for line "
                        + line.getLineId() + ": " + e.getMessage(), e);
            }
            String previousNodeId = from.getNodeId();
            for (int i = 1; i < path.size(); i++) {
                ModelCoordinate a = path.get(i - 1);
                ModelCoordinate b = path.get(i);
                String nextNodeId = i == path.size() - 1 ? to.getNodeId()
                        : from.getNodeId() + "__line_" + branchIndex + "_boundary_" + i;
                if (i < path.size() - 1) {
                    if (coordByNodeId.containsKey(nextNodeId)) {
                        throw new IllegalArgumentException("Generated boundary node ID collision: " + nextNodeId);
                    }
                    coordByNodeId.put(nextNodeId, b);
                    nodes.add(new CalculationNode(nextNodeId, line.getLineId(),
                            b.getSectionId(), b.getTrackId(), b.getPositionM(),
                            CalculationNodeType.GRID_NODE));
                }
                // Different-section endpoints here are two representations of one junction.
                double lengthM = a.getSectionId().equals(b.getSectionId())
                        ? Math.abs(b.getPositionM() - a.getPositionM()) : 0.0;
                Real resistanceOhm = Real.fromDouble(Math.max(
                        line.getResistanceOhmPerM().asDouble() * lengthM, MIN_RESISTANCE_OHM));
                String branchId = line.getLineId() + "_" + branchIndex
                        + (path.size() == 2 ? "" : "_section_" + i);
                branches.add(new CalculationBranch(branchId, line.getLineId(),
                        previousNodeId, nextNodeId, resistanceOhm, path.size() > 2 && lengthM == 0.0));
                previousNodeId = nextNodeId;
            }
        }

        for (var gear : gridModel.getSwitchGears()) {
            Map<String, String> terminalNodes = new HashMap<>();
            for (var terminal : gear.terminals()) terminalNodes.put(terminal.id(), terminal.nodeId());
            for (var cable : gear.cables()) {
                branches.add(new CalculationBranch("internal_" + gear.installationId() + "_" + cable.id(),
                        "internal_" + gear.installationId(), terminalNodes.get(cable.fromTerminalId()),
                        terminalNodes.get(cable.toTerminalId()), Real.fromDouble(cable.resistanceOhm())));
            }
        }

        Set<String> branchIds = new HashSet<>();
        for (var branch : branches) {
            if (!branchIds.add(branch.id())) throw new IllegalArgumentException("Duplicate calculation branch id: " + branch.id());
        }

        List<ElectricalElement> elements = new ArrayList<>();
        elements.addAll(branches);

        for(var connection:gridModel.getIdealConnections())
            elements.add(new org.supply.solver.model.IdealConnectionElement(connection.id(),connection.fromNodeId(),connection.toNodeId()));
        addSubstationElements(gridModel, elements);

        addFixedLoadElements(gridModel, elements);

        return new CalculationNetwork(nodes, branches, List.of(), elements);
    }





    private void addFixedLoadElements(
            GridModel gridModel,
            List<ElectricalElement> elements
    ) {
        for (Load.FixedLoad load : gridModel.fixedLoads()) {

            PowerInstallation inst = gridModel.getInstallations().stream()
                    .filter(i -> i.getInstallationId().equals(load.id()))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Missing power installation for fixed load " + load.id()
                            )
                    );

            InstallationConnection feeding =
                    singleConnection(gridModel, inst, ConnectionType.FEEDING);

            InstallationConnection returning =
                    singleConnection(gridModel, inst, ConnectionType.RETURN);

            elements.add(new FixedLoadElement(
                    load.id(),
                    feeding.getNodeId(),
                    returning.getNodeId(),
                    load.powerW()
            ));
        }
    }



    private static void addSubstationElements(
            GridModel gridModel,
            List<ElectricalElement> elements
    ) {
        for (PowerInstallation inst : gridModel.getInstallations()) {
            if (!inst.isSubstation()) {
                continue;
            }

            InstallationConnection feeding =
                    singleConnection(gridModel, inst, ConnectionType.FEEDING);

            InstallationConnection returning =
                    singleConnection(gridModel, inst, ConnectionType.RETURN);

            if (feeding.getNodeId().equals(returning.getNodeId())) {
                throw new IllegalArgumentException(
                        "Substation " + inst.getInstallationId()
                                + " has same feeding and return node: "
                                + feeding.getNodeId()
                );
            }

            switch (inst.getRectifierType()) {
                case DIODE:
                    elements.add(new DiodeSubstationElement(
                            inst.getInstallationId(),
                            feeding.getNodeId(),
                            returning.getNodeId(),
                            inst.getEmfV(),
                            inst.getInternalResistanceOhm(),
                            inst.isEnabled()
                    ));
                    break;

                case THYRISTOR:
                    elements.add(new ThyristorSubstationElement(
                            inst.getInstallationId(),
                            feeding.getNodeId(),
                            returning.getNodeId(),
                            inst.getEmfV(),
                            inst.getInternalResistanceOhm(),
                            inst.isEnabled()
                    ));
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Unsupported rectifier type for substation "
                                    + inst.getInstallationId()
                                    + ": " + inst.getRectifierType()
                    );
            }
        }
    }



    private static InstallationConnection singleConnection(
            GridModel gridModel,
            PowerInstallation inst,
            ConnectionType type
    ) {
        String foundNodeId = null;

        for (InstallationConnection connection
                : gridModel.getInstallationConnections()) {
            if (!connection.getInstallationId().equals(
                    inst.getInstallationId()
            )) {
                continue;
            }

            if (connection.getConnectionType() != type) {
                continue;
            }

            String canonicalNodeId = connection.getNodeId();

            if (foundNodeId != null
                    && !foundNodeId.equals(canonicalNodeId)) {
                throw new IllegalArgumentException(
                        "Substation "
                                + inst.getInstallationId()
                                + " has multiple electrical "
                                + type
                                + " connections"
                );
            }

            foundNodeId = canonicalNodeId;
        }

        if (foundNodeId == null) {
            throw new IllegalArgumentException(
                    "Substation "
                            + inst.getInstallationId()
                            + " missing "
                            + type
                            + " connection"
            );
        }

        return new InstallationConnection(
                inst.getInstallationId(),
                foundNodeId,
                type
        );
    }

}
