package org.supply.domain;

import java.util.Map;
import java.util.Objects;

public record SixTerminalRectifierInstallation(
        String installationId,
        boolean enabled,
        double emfV,
        double internalResistanceOhm,
        RectifierType rectifierType,
        Map<SixTerminalRectifierTerminal, String> terminalNodeIds
) {
    public SixTerminalRectifierInstallation {
        Objects.requireNonNull(
                installationId,
                "installationId"
        );
        Objects.requireNonNull(
                rectifierType,
                "rectifierType"
        );
        terminalNodeIds = Map.copyOf(
                Objects.requireNonNull(
                        terminalNodeIds,
                        "terminalNodeIds"
                )
        );

        for (SixTerminalRectifierTerminal terminal
                : SixTerminalRectifierTerminal.values()) {
            if (!terminalNodeIds.containsKey(terminal)) {
                throw new IllegalArgumentException(
                        "Missing terminal " + terminal
                                + " for installation "
                                + installationId
                );
            }
        }
    }
}