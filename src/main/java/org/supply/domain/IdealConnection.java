package org.supply.domain;

import java.util.Objects;

/** Exact electrical continuity; physical railway locations remain separate. */
public record IdealConnection(String id, String fromNodeId, String toNodeId, ConnectionType connectionType) {
    public IdealConnection {
        for (String s : new String[]{id, fromNodeId, toNodeId})
            if (s == null || s.isBlank()) throw new IllegalArgumentException("Ideal connection requires nonblank IDs");
        Objects.requireNonNull(connectionType, "connectionType");
        if (fromNodeId.equals(toNodeId)) throw new IllegalArgumentException("Ideal connection joins one node twice: " + id);
    }
}
