package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.InstallationModel;
import org.supply.domain.SixTerminalRectifierInstallation;
import org.supply.domain.SixTerminalRectifierTerminal;
import org.supply.math.Real;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationCategory;
import org.supply.domain.InstallationConnection;
import org.supply.domain.PowerInstallation;
import org.supply.domain.RectifierType;
import org.supply.model.GridModel;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

            InstallationCategory installationCategory = InstallationCategory.valueOf(
                    requireString(instConfig, "installation_category").toUpperCase()
            );

            boolean isSubstation = installationCategory == InstallationCategory.SUBSTATION;

            boolean enabled = instConfig.hasPath("enabled")
                    ? instConfig.getBoolean("enabled")
                    : true;

            Real emfV = isSubstation
                    ? requirePositiveReal(instConfig, "emf_V")
                    : Real.ZERO;

            Real internalResistanceOhm = isSubstation
                    ? requirePositiveReal(instConfig, "internal_resistance_ohm")
                    : Real.ZERO;

            RectifierType rectifierType = isSubstation
                    ? RectifierType.valueOf(
                    requireString(instConfig, "rectifier_type").toUpperCase()
            )
                    : null;

            PowerInstallation inst = new PowerInstallation(
                    installationId,
                    installationCategory,
                    enabled,
                    emfV,
                    internalResistanceOhm,
                    rectifierType
            );

            model.addInstallation(inst);

            if (instConfig.hasPath("installation_model")) {
                buildModeledInstallation(
                        model,
                        instConfig,
                        inst
                );
            }
        }
    }

    private static void buildModeledInstallation(
            GridModel model,
            Config instConfig,
            PowerInstallation installation
    ) {
        InstallationModel installationModel =
                InstallationModel.valueOf(
                        requireString(
                                instConfig,
                                "installation_model"
                        ).toUpperCase()
                );

        if (installation.getInstallationCategory()
                != InstallationCategory.SUBSTATION) {
            throw new IllegalArgumentException(
                    "installation_model requires SUBSTATION: "
                            + installation.getInstallationId()
            );
        }

        switch (installationModel) {
            case SIX_TERMINAL_RECTIFIER:
                buildSixTerminalRectifier(
                        model,
                        instConfig,
                        installation
                );
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported installation_model: "
                                + installationModel
                );
        }
    }

    private static void buildSixTerminalRectifier(
            GridModel model,
            Config instConfig,
            PowerInstallation installation
    ) {
        Config terminalsConfig =
                instConfig.getConfig("terminals");

        Map<SixTerminalRectifierTerminal, String>
                terminalNodeIds =
                new EnumMap<>(
                        SixTerminalRectifierTerminal.class
                );

        for (SixTerminalRectifierTerminal terminal
                : SixTerminalRectifierTerminal.values()) {
            String nodeId = requireString(
                    terminalsConfig,
                    terminal.name()
            );

            model.getNode(nodeId);
            terminalNodeIds.put(terminal, nodeId);

            model.addInstallationConnection(
                    new InstallationConnection(
                            installation.getInstallationId(),
                            nodeId,
                            terminal.connectionType()
                    )
            );
        }

        model.addSixTerminalRectifierInstallation(
                new SixTerminalRectifierInstallation(
                        installation.getInstallationId(),
                        installation.isEnabled(),
                        installation.getEmfV().asDouble(),
                        installation
                                .getInternalResistanceOhm()
                                .asDouble(),
                        installation.getRectifierType(),
                        terminalNodeIds
                )
        );
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