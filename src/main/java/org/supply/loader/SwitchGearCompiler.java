package org.supply.loader;

import org.supply.domain.*;
import java.util.*;

/** Compiles double-track layouts with or without a feeding gap. */
public final class SwitchGearCompiler {
    private static final List<String> ROLES = List.of("NORTH_POWER_LEFT", "NORTH_POWER_RIGHT",
            "SOUTH_POWER_LEFT", "SOUTH_POWER_RIGHT", "NORTH_RETURN", "SOUTH_RETURN");
    private static final List<String> DEFAULT_IDS = List.of("1", "3", "2", "4", "R1", "R2");
    public record Result(WiredSwitchGear gear, Map<String, String> additionalNodePositions) { }
    private SwitchGearCompiler() { }

    public static boolean hasFeedingGap(Map<String, Object> spec) {
        String model = text(spec, "model");
        if (!Set.of("DOUBLE_TRACK_GAP", "DOUBLE_TRACK_NO_GAP").contains(model))
            throw bad("Unsupported switchgear model: " + model);
        return model.equals("DOUBLE_TRACK_GAP");
    }

    public static Map<String, String> terminalIds(Map<String, Object> spec) {
        fields(spec, Set.of("model", "terminal_ids", "busbars", "connections"), "switchgear");
        hasFeedingGap(spec);
        Map<String, Object> overrides = spec.containsKey("terminal_ids") ? map(spec.get("terminal_ids")) : Map.of();
        if (!ROLES.containsAll(overrides.keySet())) throw bad("Unknown standard terminal role");
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>(Set.of("POS", "NEG"));
        for (int i = 0; i < ROLES.size(); i++) {
            String role = ROLES.get(i);
            String id = overrides.containsKey(role) ? text(overrides, role) : DEFAULT_IDS.get(i);
            identifier(id);
            if (!used.add(id)) throw bad("Duplicate/reserved terminal id: " + id);
            result.put(role, id);
        }
        return Collections.unmodifiableMap(result);
    }

    public static Result compile(String installationId, Map<String, Object> spec,
                                 Map<String, Object> conductorTypes, Map<String, String> externalNodes,
                                 String busPosition) {
        var ids = terminalIds(spec);
        boolean gap = hasFeedingGap(spec);
        for (String side : List.of("NORTH", "SOUTH")) {
            String left = externalNodes.get(side + "_POWER_LEFT"), right = externalNodes.get(side + "_POWER_RIGHT");
            if (left == null || right == null || left.equals(right) == gap)
                throw bad("Inconsistent feeding nodes for " + installationId + ": " + side + " requires " + (gap ? "separate nodes" : "one shared node"));
        }
        List<SwitchGearTerminal> terminals = new ArrayList<>();
        Set<String> used = new HashSet<>(ids.values());
        for (String role : ROLES) {
            String node = externalNodes.get(role);
            if (node == null) throw bad("Missing external terminal node: " + role);
            terminals.add(new SwitchGearTerminal(ids.get(role), node,
                    role.endsWith("RETURN") ? ConnectionType.RETURN : ConnectionType.FEEDING));
        }
        Map<String, Object> buses = new LinkedHashMap<>();
        buses.put("POS", "FEEDING"); buses.put("NEG", "RETURN");
        if (spec.containsKey("busbars")) {
            for (var entry : map(spec.get("busbars")).entrySet()) {
                if (buses.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw bad("POS and NEG are reserved rectifier connection points");
            }
        }
        Map<String, String> positions = new LinkedHashMap<>();
        for (var entry : buses.entrySet()) {
            identifier(entry.getKey());
            if (!used.add(entry.getKey())) throw bad("Busbar duplicates external terminal: " + entry.getKey());
            String node = installationId + ".SG." + entry.getKey();
            terminals.add(new SwitchGearTerminal(entry.getKey(), node,
                    ConnectionType.valueOf(text(buses, entry.getKey()))));
            positions.put(node, busPosition);
        }
        Object connectionValue = spec.get("connections");
        if (!(connectionValue instanceof List<?> connections) || connections.isEmpty())
            throw bad("switchgear.connections must be a nonempty list");
        List<CableConnection> cables = new ArrayList<>();
        for (Object value : connections) {
            var cable = map(value);
            fields(cable, Set.of("id", "from", "to", "length_m", "conductor_type", "multiplicity"), "cable");
            String cableId = text(cable, "id"); identifier(cableId);
            String type = text(cable, "conductor_type");
            if (!conductorTypes.containsKey(type)) throw bad("Unknown conductor_type: " + type);
            var conductor = map(conductorTypes.get(type));
            fields(conductor, Set.of("resistance_ohm_per_km"), "conductor type " + type);
            double multiplicity = number(cable, "multiplicity");
            if (multiplicity < 1 || multiplicity > Integer.MAX_VALUE || multiplicity != Math.rint(multiplicity))
                throw bad("multiplicity must be a positive integer: " + cableId);
            cables.add(new CableConnection(cableId, text(cable, "from"), text(cable, "to"),
                    number(cable, "length_m"), new ConductorType(type, number(conductor, "resistance_ohm_per_km")),
                    (int) multiplicity));
        }
        var gear = new WiredSwitchGear(installationId, terminals, cables);
        // Every declared terminal/bus must have a cable path to the appropriate rectifier pole.
        Map<String, Set<String>> graph = new HashMap<>();
        terminals.forEach(t -> graph.put(t.id(), new HashSet<>()));
        Map<String, String> firstTerminalByNode = new HashMap<>();
        for (var terminal : terminals) {
            String first = firstTerminalByNode.putIfAbsent(terminal.nodeId(), terminal.id());
            if (first != null) { graph.get(first).add(terminal.id()); graph.get(terminal.id()).add(first); }
        }
        cables.forEach(c -> { graph.get(c.fromTerminalId()).add(c.toTerminalId()); graph.get(c.toTerminalId()).add(c.fromTerminalId()); });
        for (ConnectionType type : ConnectionType.values()) {
            Set<String> seen = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>(); queue.add(type == ConnectionType.FEEDING ? "POS" : "NEG");
            while (!queue.isEmpty()) { String t = queue.removeFirst(); if (seen.add(t)) queue.addAll(graph.get(t)); }
            for (var terminal : terminals) if (terminal.connectionType() == type && !seen.contains(terminal.id()))
                throw bad("Terminal has no cable path to rectifier: " + terminal.id());
        }
        return new Result(gear, Collections.unmodifiableMap(positions));
    }

    public static Map<String, Object> generated(WiredSwitchGear gear) {
        List<Map<String, Object>> terminals = new ArrayList<>(), cables = new ArrayList<>();
        gear.terminals().forEach(t -> terminals.add(Map.of("id", t.id(), "node_id", t.nodeId(), "connection_type", t.connectionType().name())));
        gear.cables().forEach(c -> cables.add(Map.of("id", c.id(), "from", c.fromTerminalId(), "to", c.toTerminalId(),
                "length_m", c.lengthM(), "conductor_type", c.conductorType().id(),
                "resistance_ohm_per_km", c.conductorType().resistanceOhmPerKm(), "multiplicity", c.multiplicity())));
        return Map.of("installation_id", gear.installationId(), "terminals", terminals, "cables", cables);
    }
    private static void fields(Map<String, Object> map, Set<String> allowed, String label) {
        for (String key : map.keySet()) if (!allowed.contains(key)) throw bad("Unknown " + label + " field: " + key);
    }
    private static void identifier(String s) { if (!s.matches("[A-Za-z0-9_-]+")) throw bad("Invalid terminal/cable identifier: " + s); }
    private static String text(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof String value) || value.isBlank()) throw bad("Nonblank string required: " + key);
        return value;
    }
    private static double number(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof Number value) || !Double.isFinite(value.doubleValue())) throw bad("Finite number required: " + key);
        return value.doubleValue();
    }
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> input)) throw bad("Object required");
        Map<String, Object> result = new LinkedHashMap<>(); input.forEach((k,v) -> result.put((String) k,v)); return result;
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
}
