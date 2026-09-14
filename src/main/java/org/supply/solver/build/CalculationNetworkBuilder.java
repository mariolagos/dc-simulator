package org.supply.solver.build;

import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationConnection;
import org.supply.domain.Line;
import org.supply.domain.Load;
import org.supply.domain.Node;
import org.supply.domain.PowerInstallation;
import org.supply.domain.SixTerminalRectifierInstallation;
import org.supply.domain.SixTerminalRectifierTerminal;
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

        ElectricalNodeAliases aliases =
                ElectricalNodeAliases.from(gridModel);


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

            double lengthM = Math.abs(toModel.getPositionM() - fromModel.getPositionM());

            double rawResistanceOhm =
                    line.getResistanceOhmPerM().asDouble() * lengthM;

            Real resistanceOhm = Real.fromDouble(
                    Math.max(rawResistanceOhm, MIN_RESISTANCE_OHM)
            );

            branches.add(new CalculationBranch(
                    line.getLineId() + "_" + branchIndex,
                    line.getLineId(),
                    from.getNodeId(),
                    to.getNodeId(),
                    resistanceOhm
            ));
        }

        addSixTerminalInternalBranches(
                gridModel,
                branches
        );

        List<ElectricalElement> elements = new ArrayList<>();
        elements.addAll(branches);

        addSubstationElements(gridModel, elements, aliases);

        addFixedLoadElements(gridModel, elements, aliases);

        return new CalculationNetwork(nodes, branches, List.of(), elements);
    }

    private static void addSixTerminalInternalBranches(
            GridModel gridModel,
            List<CalculationBranch> branches
    ) {
        for (SixTerminalRectifierInstallation installation
                : gridModel.getSixTerminalRectifierInstallations()) {
            Map<SixTerminalRectifierTerminal, String> terminals =
                    installation.terminalNodeIds();

            String powerBus = terminals.get(
                    SixTerminalRectifierTerminal.NORTH_POWER_LEFT
            );

            addInternalBranch(
                    branches,
                    installation.installationId(),
                    "north_power_right",
                    powerBus,
                    terminals.get(
                            SixTerminalRectifierTerminal.NORTH_POWER_RIGHT
                    )
            );

            addInternalBranch(
                    branches,
                    installation.installationId(),
                    "south_power_left",
                    powerBus,
                    terminals.get(
                            SixTerminalRectifierTerminal.SOUTH_POWER_LEFT
                    )
            );

            addInternalBranch(
                    branches,
                    installation.installationId(),
                    "south_power_right",
                    powerBus,
                    terminals.get(
                            SixTerminalRectifierTerminal.SOUTH_POWER_RIGHT
                    )
            );

            addInternalBranch(
                    branches,
                    installation.installationId(),
                    "south_return",
                    terminals.get(
                            SixTerminalRectifierTerminal.NORTH_RETURN
                    ),
                    terminals.get(
                            SixTerminalRectifierTerminal.SOUTH_RETURN
                    )
            );
        }
    }

    private static void addInternalBranch(
            List<CalculationBranch> branches,
            String installationId,
            String connectionId,
            String fromNodeId,
            String toNodeId
    ) {
        branches.add(new CalculationBranch(
                "internal_" + installationId
                        + "_" + connectionId,
                "internal_" + installationId,
                fromNodeId,
                toNodeId,
                Real.fromDouble(MIN_RESISTANCE_OHM)
        ));
    }

    private void addFixedLoadElements(
            GridModel gridModel,
            List<ElectricalElement> elements,
            ElectricalNodeAliases aliases
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
                    singleConnection(gridModel, inst, ConnectionType.FEEDING, aliases);

            InstallationConnection returning =
                    singleConnection(gridModel, inst, ConnectionType.RETURN, aliases);

            elements.add(new FixedLoadElement(
                    load.id(),
                    feeding.getNodeId(),
                    returning.getNodeId(),
                    load.powerW()
            ));
        }
    }

    private record FixedLoadConnection(
            String feedingNodeId,
            String returnNodeId
    ) {
    }

    private static void addSubstationElements(
            GridModel gridModel,
            List<ElectricalElement> elements,
            ElectricalNodeAliases aliases
    ) {
        for (PowerInstallation inst : gridModel.getInstallations()) {
            if (!inst.isSubstation()) {
                continue;
            }

            InstallationConnection feeding =
                    singleConnection(gridModel, inst, ConnectionType.FEEDING, aliases);

            InstallationConnection returning =
                    singleConnection(gridModel, inst, ConnectionType.RETURN, aliases);

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

    private static InstallationConnection singleConnection(
            GridModel gridModel,
            PowerInstallation inst,
            ConnectionType type,
            ElectricalNodeAliases aliases
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

            String canonicalNodeId =
                    aliases.canonicalNodeId(
                            connection.getNodeId()
                    );

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
