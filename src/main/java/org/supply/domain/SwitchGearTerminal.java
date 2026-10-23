package org.supply.domain;

import java.util.Objects;

/** Customer terminal identity is separate from conductor polarity and the electrical node. */
public record SwitchGearTerminal(String id, String nodeId, ConnectionType connectionType) {
    public SwitchGearTerminal {
        if (id == null || id.isBlank() || nodeId == null || nodeId.isBlank())
            throw new IllegalArgumentException("Switchgear terminal requires id and nodeId");
        Objects.requireNonNull(connectionType, "connectionType");
    }
}
