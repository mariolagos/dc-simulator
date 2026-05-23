package org.supply.model;

import org.supply.domain.Line;
import org.supply.domain.Node;
import org.supply.domain.InstallationConnection;
import org.supply.domain.PowerInstallation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GridModel {

    private final Map<String, Node> nodes = new HashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<PowerInstallation> installations = new ArrayList<>();
    private final List<InstallationConnection> installationConnections = new ArrayList<>();

    // --- Nodes ---
    public void addNode(Node node) {
        nodes.put(node.getNodeId(), node);
    }

    public Node getNode(String nodeId) {
        Node node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        return node;
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    // --- Lines ---
    public void addLine(Line line) {
        lines.add(line);
    }

    public List<Line> getLines() {
        return lines;
    }

    // --- Installations ---
    public void addInstallation(PowerInstallation inst) {
        installations.add(inst);
    }

    public List<PowerInstallation> getInstallations() {
        return installations;
    }

    // --- Power connections ---
    public void addInstallationConnection(InstallationConnection connection) {
        installationConnections.add(connection);
    }

    public void addInstallationConnections(Collection<InstallationConnection> connections) {
        installationConnections.addAll(connections);
    }

    public List<InstallationConnection> getInstallationConnections() {
        return installationConnections;
    }
}