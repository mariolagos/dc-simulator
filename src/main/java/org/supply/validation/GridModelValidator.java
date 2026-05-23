package org.supply.validation;

import org.dcsim.math.Real;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationType;
import org.supply.domain.Line;
import org.supply.domain.Node;
import org.supply.domain.InstallationConnection;
import org.supply.domain.PowerInstallation;
import org.supply.model.GridModel;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class GridModelValidator {

    public void validate(GridModel model) {
        Objects.requireNonNull(model, "model");

        validateNodes(model);
        validateLines(model);
        validateInstallations(model);
        validateInstallationConnections(model);
        validateSubstationConnections(model);
    }

    private void validateNodes(GridModel model) {
        Set<String> seenNodeIds = new HashSet<>();

        for (Node node : model.getNodes()) {
            Objects.requireNonNull(node, "Node must not be null");

            String nodeId = requireNonBlank(node.getNodeId(), "node_id");
            if (!seenNodeIds.add(nodeId)) {
                throw new IllegalArgumentException("Duplicate node_id: " + nodeId);
            }

            requireNonBlank(node.getPositionRwy(), "position_rwy for node_id=" + nodeId);
        }
    }

    private void validateLines(GridModel model) {
        for (Line line : model.getLines()) {
            Objects.requireNonNull(line, "Line must not be null");

            Node fromNode = Objects.requireNonNull(line.getFromNode(), "Line.fromNode");
            Node toNode = Objects.requireNonNull(line.getToNode(), "Line.toNode");

            String fromNodeId = requireNonBlank(fromNode.getNodeId(), "line.from.node_id");
            String toNodeId = requireNonBlank(toNode.getNodeId(), "line.to.node_id");

            if (fromNodeId.equals(toNodeId)) {
                throw new IllegalArgumentException(
                        "Line must not connect same node twice: " + fromNodeId
                );
            }

            model.getNode(fromNodeId);
            model.getNode(toNodeId);

            Real resistanceOhmPerM = Objects.requireNonNull(
                    line.getResistanceOhmPerM(),
                    "line.resistance_ohm_per_m"
            );

            if (resistanceOhmPerM.asDouble() <= 0.0) {
                throw new IllegalArgumentException(
                        "Line resistance_ohm_per_m must be > 0: "
                                + fromNodeId + " -> " + toNodeId
                );
            }
        }
    }

    private void validateInstallations(GridModel model) {
        Set<String> seenInstallationIds = new HashSet<>();

        for (PowerInstallation installation : model.getInstallations()) {
            Objects.requireNonNull(installation, "PowerInstallation must not be null");

            String installationId = requireNonBlank(
                    installation.getInstallationId(),
                    "installation_id"
            );

            if (!seenInstallationIds.add(installationId)) {
                throw new IllegalArgumentException("Duplicate installation_id: " + installationId);
            }

            InstallationType installationType = Objects.requireNonNull(
                    installation.getInstallationType(),
                    "installation_type for installation_id=" + installationId
            );

            if (installationType == InstallationType.SUBSTATION) {
                Real emfV = Objects.requireNonNull(
                        installation.getEmfV(),
                        "emf_V for installation_id=" + installationId
                );
                if (emfV.asDouble() <= 0.0) {
                    throw new IllegalArgumentException(
                            "emf_V must be > 0 for SUBSTATION installation_id=" + installationId
                    );
                }

                Real internalResistanceOhm = Objects.requireNonNull(
                        installation.getInternalResistanceOhm(),
                        "internal_resistance_ohm for installation_id=" + installationId
                );
                if (internalResistanceOhm.asDouble() <= 0.0) {
                    throw new IllegalArgumentException(
                            "internal_resistance_ohm must be > 0 for SUBSTATION installation_id=" + installationId
                    );
                }

                if (installation.getRectifierType() == null) {
                    throw new IllegalArgumentException(
                            "rectifier_type is required for SUBSTATION installation_id=" + installationId
                    );
                }
            }

            if (installationType == InstallationType.POINT) {
                if (installation.getEmfV() == null || installation.getInternalResistanceOhm() == null) {
                    throw new IllegalArgumentException(
                            "POINT installation must have neutral electrical placeholders, installation_id="
                                    + installationId
                    );
                }
            }
        }
    }

    private void validateInstallationConnections(GridModel model) {
        for (InstallationConnection connection : model.getInstallationConnections()) {
            Objects.requireNonNull(connection, "InstallationConnection must not be null");

            String installationId = requireNonBlank(
                    connection.getInstallationId(),
                    "installation_connections.installation_id"
            );

            String nodeId = requireNonBlank(
                    connection.getNodeId(),
                    "installation_connections.node_id"
            );

            ConnectionType connectionType = Objects.requireNonNull(
                    connection.getConnectionType(),
                    "installation_connections.connection_type"
            );

            requireInstallation(model, installationId);
            model.getNode(nodeId);

            if (connectionType != ConnectionType.FEEDING && connectionType != ConnectionType.RETURN) {
                throw new IllegalArgumentException(
                        "Unsupported connection_type for installation_id="
                                + installationId + ", node_id=" + nodeId + ": " + connectionType
                );
            }
        }
    }

    private void validateSubstationConnections(GridModel model) {
        for (PowerInstallation installation : model.getInstallations()) {
            if (installation.getInstallationType() != InstallationType.SUBSTATION) {
                continue;
            }

            String installationId = installation.getInstallationId();
            int feedingCount = 0;
            int returnCount = 0;

            for (InstallationConnection connection : model.getInstallationConnections()) {
                if (!installationId.equals(connection.getInstallationId())) {
                    continue;
                }

                if (connection.getConnectionType() == ConnectionType.FEEDING) {
                    feedingCount++;
                } else if (connection.getConnectionType() == ConnectionType.RETURN) {
                    returnCount++;
                }
            }

            if (feedingCount != 1 || returnCount != 1) {
                throw new IllegalArgumentException(
                        "SUBSTATION must have exactly 1 FEEDING and 1 RETURN connection: installation_id="
                                + installationId
                                + " (FEEDING=" + feedingCount + ", RETURN=" + returnCount + ")"
                );
            }
        }
    }

    private static PowerInstallation requireInstallation(GridModel model, String installationId) {
        return model.getInstallations().stream()
                .filter(i -> installationId.equals(i.getInstallationId()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown installation_id: " + installationId));
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing or blank " + fieldName);
        }
        return value;
    }
}