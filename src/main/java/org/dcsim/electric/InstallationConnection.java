package org.dcsim.electric;

import java.util.Objects;

public class InstallationConnection {

    private final String installation_id;
    private final String node_id;
    private final ConnectionType connection_type;

    public InstallationConnection(
            String installation_id,
            String node_id,
            ConnectionType connection_type
    ) {
        this.installation_id = Objects.requireNonNull(installation_id);
        this.node_id = Objects.requireNonNull(node_id);
        this.connection_type = Objects.requireNonNull(connection_type);
    }

    public String getInstallationId() {
        return installation_id;
    }

    public String getNodeId() {
        return node_id;
    }

    public ConnectionType getConnectionType() {
        return connection_type;
    }

    public boolean isFeeding() {
        return connection_type == ConnectionType.FEEDING;
    }

    public boolean isReturn() {
        return connection_type == ConnectionType.RETURN;
    }

    @Override
    public String toString() {
        return "InstallationConnection{" +
                "installation_id='" + installation_id + '\'' +
                ", node_id='" + node_id + '\'' +
                ", connection_type=" + connection_type +
                '}';
    }
}