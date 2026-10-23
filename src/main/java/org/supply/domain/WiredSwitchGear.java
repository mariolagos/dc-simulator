package org.supply.domain;

import java.util.*;

/** Explicit wiring, independent of the number of tracks, terminals or busbars. */
public record WiredSwitchGear(String installationId, List<SwitchGearTerminal> terminals,
                              List<CableConnection> cables) implements SwitchGear {
    public WiredSwitchGear {
        if (installationId == null || installationId.isBlank())
            throw new IllegalArgumentException("Switchgear requires installationId");
        terminals = List.copyOf(terminals);
        cables = List.copyOf(cables);
        Map<String, SwitchGearTerminal> byId = new HashMap<>();
        for (var terminal : terminals) {
            if (byId.putIfAbsent(terminal.id(), terminal) != null)
                throw new IllegalArgumentException("Duplicate switchgear terminal: " + terminal.id());
        }
        // Different roles must never alias the same electrical node.
        Map<String, ConnectionType> nodeTypes = new HashMap<>();
        for (var terminal : terminals) {
            var previous = nodeTypes.putIfAbsent(terminal.nodeId(), terminal.connectionType());
            if (previous != null && previous != terminal.connectionType())
                throw new IllegalArgumentException("Feeding/return node alias: " + terminal.nodeId());
        }
        Set<String> ids = new HashSet<>();
        for (var cable : cables) {
            if (!ids.add(cable.id())) throw new IllegalArgumentException("Duplicate cable: " + cable.id());
            var from = byId.get(cable.fromTerminalId());
            var to = byId.get(cable.toTerminalId());
            if (from == null || to == null) throw new IllegalArgumentException("Unknown cable terminal: " + cable.id());
            if (from.connectionType() != to.connectionType())
                throw new IllegalArgumentException("Cable joins feeding and return: " + cable.id());
            if (from.nodeId().equals(to.nodeId()))
                throw new IllegalArgumentException("Cable endpoints share one electrical node: " + cable.id());
        }
    }
}
