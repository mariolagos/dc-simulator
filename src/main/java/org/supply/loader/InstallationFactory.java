package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueType;
import org.supply.domain.Node;
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

    /** Generate compact terminal nodes before LineFactory resolves its endpoints. */
    public void prepareTerminalNodes(GridModel model, Config gridConfig) {
        if (!gridConfig.hasPath("power_installations")) return;
        for (Config inst : gridConfig.getConfigList("power_installations")) {
            if (!inst.hasPath("installation_model")) continue;
            InstallationModel kind = InstallationModel.valueOf(
                    requireString(inst, "installation_model").toUpperCase(java.util.Locale.ROOT));
            if (kind != InstallationModel.SIX_TERMINAL_RECTIFIER) continue;
            if (!"SUBSTATION".equalsIgnoreCase(requireString(inst, "installation_category")))
                throw new IllegalArgumentException("installation_model requires SUBSTATION");
            Config terminals = inst.hasPath("terminals") ? inst.getConfig("terminals") : null;
            if (terminals != null) {
                for (String name : terminals.root().keySet()) {
                    try { SixTerminalRectifierTerminal.valueOf(name); }
                    catch (IllegalArgumentException failure) {
                        throw new IllegalArgumentException("Unknown terminal " + name + " for "
                                + requireString(inst, "installation_id"), failure);
                    }
                }
            }
            for (SixTerminalRectifierTerminal terminal : SixTerminalRectifierTerminal.values()) {
                if (terminals != null && terminals.hasPath(terminal.name())
                        && terminals.getValue(terminal.name()).valueType() == ConfigValueType.STRING)
                    continue; // Existing format: reference an explicitly declared grid node.
                Config spec = terminalSpec(inst, terminal);
                String position = spec != null && spec.hasPath("position_rwy")
                        ? requireString(spec, "position_rwy") : requireString(inst, "position_rwy");
                String nodeId = terminalNodeId(inst, terminal);
                if (model.getNodes().stream().anyMatch(n -> nodeId.equals(n.getNodeId())))
                    throw new IllegalArgumentException("Generated terminal node already exists: " + nodeId);
                model.addNode(new Node(nodeId, position));
            }
        }
    }

    private static Config terminalSpec(Config inst, SixTerminalRectifierTerminal terminal) {
        if (!inst.hasPath("terminals")) return null;
        Config terminals = inst.getConfig("terminals");
        String name = terminal.name();
        if (!terminals.hasPath(name))
            throw new IllegalArgumentException("Missing terminal " + name + " for "
                    + requireString(inst, "installation_id"));
        ConfigValueType type = terminals.getValue(name).valueType();
        if (type == ConfigValueType.STRING) return null;
        if (type != ConfigValueType.OBJECT)
            throw new IllegalArgumentException("Terminal " + name + " must be a node ID or object");
        return terminals.getConfig(name);
    }

    private static String terminalNodeId(Config inst, SixTerminalRectifierTerminal terminal) {
        Config terminals = inst.hasPath("terminals") ? inst.getConfig("terminals") : null;
        if (terminals != null && terminals.hasPath(terminal.name())
                && terminals.getValue(terminal.name()).valueType() == ConfigValueType.STRING)
            return requireString(terminals, terminal.name());
        Config spec = terminalSpec(inst, terminal);
        if (spec != null && spec.hasPath("node_id")) return requireString(spec, "node_id");
        String id = requireString(inst, "installation_id");
        return switch (terminal) {
            case NORTH_POWER_LEFT -> "F_" + id + "_A";
            case NORTH_POWER_RIGHT -> "F_" + id + "_B";
            case SOUTH_POWER_LEFT -> "F_" + id + "_C";
            case SOUTH_POWER_RIGHT -> "F_" + id + "_D";
            case NORTH_RETURN -> "R_" + id + "_N";
            case SOUTH_RETURN -> "R_" + id + "_S";
        };
    }

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
        Map<SixTerminalRectifierTerminal, String>
                terminalNodeIds =
                new EnumMap<>(
                        SixTerminalRectifierTerminal.class
                );

        for (SixTerminalRectifierTerminal terminal
                : SixTerminalRectifierTerminal.values()) {
            String nodeId = terminalNodeId(instConfig, terminal);

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