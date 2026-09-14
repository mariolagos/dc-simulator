package org.supply.solver.build;

import org.supply.domain.SixTerminalRectifierInstallation;
import org.supply.domain.SixTerminalRectifierTerminal;
import org.supply.model.GridModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ElectricalNodeAliases {

    private final Map<String, String> representativeByNodeId =
            new HashMap<>();

    static ElectricalNodeAliases from(
            GridModel gridModel
    ) {
        ElectricalNodeAliases aliases =
                new ElectricalNodeAliases();

        for (SixTerminalRectifierInstallation installation
                : gridModel
                .getSixTerminalRectifierInstallations()) {

            aliases.addGroup(
                    installation,
                    SixTerminalRectifierTerminal
                            .NORTH_POWER_LEFT,
                    List.of(
                            SixTerminalRectifierTerminal
                                    .NORTH_POWER_LEFT,
                            SixTerminalRectifierTerminal
                                    .NORTH_POWER_RIGHT,
                            SixTerminalRectifierTerminal
                                    .SOUTH_POWER_LEFT,
                            SixTerminalRectifierTerminal
                                    .SOUTH_POWER_RIGHT
                    )
            );

            aliases.addGroup(
                    installation,
                    SixTerminalRectifierTerminal.NORTH_RETURN,
                    List.of(
                            SixTerminalRectifierTerminal.NORTH_RETURN,
                            SixTerminalRectifierTerminal.SOUTH_RETURN
                    )
            );
        }

        return aliases;
    }

    String canonicalNodeId(String nodeId) {
        return representativeByNodeId.getOrDefault(
                nodeId,
                nodeId
        );
    }

    private void addGroup(
            SixTerminalRectifierInstallation installation,
            SixTerminalRectifierTerminal representativeTerminal,
            List<SixTerminalRectifierTerminal> terminals
    ) {
        String representativeNodeId =
                installation.terminalNodeIds().get(
                        representativeTerminal
                );

        for (SixTerminalRectifierTerminal terminal : terminals) {
            String nodeId =
                    installation.terminalNodeIds().get(terminal);

            String previous =
                    representativeByNodeId.putIfAbsent(
                            nodeId,
                            representativeNodeId
                    );

            if (previous != null
                    && !previous.equals(representativeNodeId)) {
                throw new IllegalArgumentException(
                        "Node " + nodeId
                                + " belongs to incompatible electrical "
                                + "groups for installation "
                                + installation.installationId()
                );
            }
        }
    }
}