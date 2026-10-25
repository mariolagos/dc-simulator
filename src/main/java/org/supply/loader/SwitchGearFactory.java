package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.*;
import org.supply.model.GridModel;
import java.util.*;

/** Loads generated internal wiring without altering Line or the legacy six-terminal model. */
public final class SwitchGearFactory {
    public void build(GridModel model, Config grid) {
        if (!grid.hasPath("switchgears")) return;
        Set<String> ids = new HashSet<>();
        for (Config spec : grid.getConfigList("switchgears")) {
            String id = spec.getString("installation_id");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate switchgear: " + id);
            var source = model.getInstallations().stream().filter(i -> i.getInstallationId().equals(id))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown switchgear installation: " + id));
            if (!source.isSubstation())
                throw new IllegalArgumentException("Switchgear requires a plain SUBSTATION: " + id);
            List<SwitchGearTerminal> terminals = new ArrayList<>();
            for (Config terminal : spec.getConfigList("terminals")) {
                String node = terminal.getString("node_id");
                model.getNode(node);
                terminals.add(new SwitchGearTerminal(terminal.getString("id"), node,
                        ConnectionType.valueOf(terminal.getString("connection_type"))));
            }
            List<CableConnection> cables = new ArrayList<>();
            for (Config cable : spec.getConfigList("cables")) {
                cables.add(new CableConnection(cable.getString("id"), cable.getString("from"),
                        cable.getString("to"), cable.getDouble("length_m"),
                        new ConductorType(cable.getString("conductor_type"), cable.getDouble("resistance_ohm_per_km")),
                        cable.getInt("multiplicity")));
            }
            var gear = new WiredSwitchGear(id, terminals, cables);
            validateSourcePaths(model, gear, spec.hasPath("disconnected_terminals")
                    ? Set.copyOf(spec.getStringList("disconnected_terminals")) : Set.of());
            model.addSwitchGear(gear);
        }
    }
    private static void validateSourcePaths(GridModel model, SwitchGear gear, Set<String> disconnected) {
        Map<String, SwitchGearTerminal> byNode = new HashMap<>();
        Map<String, Set<String>> graph = new HashMap<>();
        for (var terminal : gear.terminals()) {
            byNode.putIfAbsent(terminal.nodeId(), terminal);
            graph.put(terminal.id(), new HashSet<>());
        }
        // Different terminal labels may identify the same continuous rail node.
        for (var terminal : gear.terminals()) {
            var first = byNode.get(terminal.nodeId());
            if (!first.id().equals(terminal.id())) {
                graph.get(first.id()).add(terminal.id());graph.get(terminal.id()).add(first.id());
            }
        }
        for (var cable : gear.cables()) {
            graph.get(cable.fromTerminalId()).add(cable.toTerminalId());
            graph.get(cable.toTerminalId()).add(cable.fromTerminalId());
        }
        if(!graph.keySet().containsAll(disconnected)) throw new IllegalArgumentException("Unknown disconnected terminal");
        for(var link:model.getIdealConnections()) {
            var a=byNode.get(link.fromNodeId()); var b=byNode.get(link.toNodeId());
            if(a!=null && b!=null) { graph.get(a.id()).add(b.id());graph.get(b.id()).add(a.id()); }
        }
        for (var type : ConnectionType.values()) {
            var connections = model.getInstallationConnections().stream()
                    .filter(c -> c.getInstallationId().equals(gear.installationId()) && c.getConnectionType() == type).toList();
            if (connections.size() != 1) throw new IllegalArgumentException("Switchgear source needs one connection per pole");
            var sourceTerminal = byNode.get(connections.get(0).getNodeId());
            if (sourceTerminal == null || sourceTerminal.connectionType() != type)
                throw new IllegalArgumentException("Source pole is not a matching switchgear terminal: " + gear.installationId());
            Set<String> seen = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>(); queue.add(sourceTerminal.id());
            while (!queue.isEmpty()) { var id = queue.removeFirst(); if (seen.add(id)) queue.addAll(graph.get(id)); }
            for (var terminal : gear.terminals()) if (terminal.connectionType() == type && !disconnected.contains(terminal.id()) && !seen.contains(terminal.id()))
                throw new IllegalArgumentException("Switchgear terminal has no path to source pole: " + terminal.id());
        }
    }

}
