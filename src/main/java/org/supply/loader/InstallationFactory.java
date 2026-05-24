package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.math.Real;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationType;
import org.supply.domain.InstallationConnection;
import org.supply.domain.PowerInstallation;
import org.supply.domain.RectifierType;
import org.supply.model.GridModel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.supply.utils.ConfigUtils.requirePositiveReal;
import static org.supply.utils.ConfigUtils.requireString;

public class InstallationFactory {

    public void build(GridModel model, Config gridConfig) {
        buildInstallations(model, gridConfig);
        buildInstallationConnections(model, gridConfig);
    }

    private static void buildInstallations(GridModel model, Config gridConfig) {
        if (!gridConfig.hasPath("power_installations")) {
            return;
        }

        Set<String> seenIds = new HashSet<>();
        List<? extends Config> installations = gridConfig.getConfigList("power_installations");

        for (Config instConfig : installations) {
            String installationId = requireString(instConfig, "installation_id");
            if (!seenIds.add(installationId)) {
                throw new IllegalArgumentException("Duplicate installation_id: " + installationId);
            }

            InstallationType installationType = InstallationType.valueOf(
                    requireString(instConfig, "installation_type").toUpperCase()
            );

            boolean isSubstation = installationType == InstallationType.SUBSTATION;

            Real emfV = isSubstation
                    ? requirePositiveReal(instConfig, "emf_V")
                    : Real.ZERO;

            Real internalResistanceOhm = isSubstation
                    ? requirePositiveReal(instConfig, "internal_resistance_ohm")
                    : Real.ZERO;

            RectifierType rectifierType = RectifierType.valueOf(
                    requireString(instConfig, "rectifier_type").toUpperCase()
            );

            PowerInstallation inst = new PowerInstallation(
                    installationId,
                    installationType,
                    emfV,
                    internalResistanceOhm,
                    rectifierType
            );

            model.addInstallation(inst);
        }
    }

    private static void buildInstallationConnections(GridModel model, Config gridConfig) {
        if (!gridConfig.hasPath("installation_connections")) {
            return;
        }

        List<? extends Config> connectionList = gridConfig.getConfigList("installation_connections");

        for (Config conf : connectionList) {
            String installationId = requireString(conf, "installation_id");
            String nodeId = requireString(conf, "node_id");

            ConnectionType connectionType = ConnectionType.valueOf(
                    requireString(conf, "connection_type").toUpperCase()
            );

            // Validera tidigt att referenserna finns
            model.getNode(nodeId);
            requireInstallation(model, installationId);

            model.addInstallationConnection(new InstallationConnection(
                    installationId,
                    nodeId,
                    connectionType
            ));
        }
    }

    private static PowerInstallation requireInstallation(GridModel model, String installationId) {
        return model.getInstallations().stream()
                .filter(i -> i.getInstallationId().equals(installationId))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown installation_id: " + installationId));
    }
}