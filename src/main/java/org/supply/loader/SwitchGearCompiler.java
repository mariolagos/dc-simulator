package org.supply.loader;

import org.supply.domain.*;
import java.util.*;

/** Compiles explicit port-to-terminal wiring, independent of station topology. */
public final class SwitchGearCompiler {
    public record Result(WiredSwitchGear gear, Map<String, String> additionalNodePositions, Map<ConnectionType,String> sourceNodes) { }
    private SwitchGearCompiler() { }

    /** Keys are port.track.F/R; values are customer terminal identities. */
    public static Map<String, String> terminalIds(Map<String, Object> spec) {
        fields(spec, Set.of("terminals", "busbars", "connections", "joins", "source_terminals"), "switchgear");
        var terminals = map(spec.get("terminals"));
        if (terminals.isEmpty()) throw bad("switchgear.terminals must not be empty");
        Map<String,String> result = new TreeMap<>();
        Map<String,ConnectionType> types = new HashMap<>();
        Map<String,Set<String>> ports = new TreeMap<>();
        for (String role : terminals.keySet()) {
            if (!role.matches("[\\p{L}\\p{Nd}_-]+\\.[NS]\\.[FR]")) throw bad("Terminal address must be port.track.F/R: " + role);
            String id = text(terminals,role); identifier(id);
            var previous = types.putIfAbsent(id,connectionType(role));
            if (previous != null && previous != connectionType(role)) throw bad("Feeding/return terminal alias: " + id);
            result.put(role,id);
            String port=role.substring(0,role.indexOf('.'));
            ports.computeIfAbsent(port,k->new HashSet<>()).add(role.substring(role.indexOf('.')+1));
        }
        for (var port : ports.entrySet()) if (!port.getValue().equals(Set.of("N.F","S.F","N.R","S.R")))
            throw bad("Port requires N.F, S.F, N.R and S.R: " + port.getKey());
        return Collections.unmodifiableMap(result);
    }
    public static ConnectionType connectionType(String role) {
        return role.endsWith(".R") ? ConnectionType.RETURN : ConnectionType.FEEDING;
    }
    /** Ideal continuity is declared explicitly; cable resistance remains separate. */
    public static List<List<String>> joins(Map<String,Object> spec) {
        if (!spec.containsKey("joins")) return List.of();
        if (!(spec.get("joins") instanceof List<?> list)) throw bad("switchgear.joins must be a list");
        var ids=terminalIds(spec);
        List<List<String>> result=new ArrayList<>();
        Set<Set<String>> seen=new HashSet<>();
        for (Object value:list) {
            if (!(value instanceof List<?> group) || group.size()<2) throw bad("A join needs at least two terminals");
            List<String> members=new ArrayList<>();Set<ConnectionType> types=new HashSet<>();
            for (Object item:group) {
                if (!(item instanceof String id) || !ids.containsValue(id)) throw bad("Join must reference an external terminal: " + item);
                if (members.contains(id)) throw bad("Repeated terminal in join: " + id);
                members.add(id);
                ids.forEach((role,terminal)->{if(terminal.equals(id))types.add(connectionType(role));});
            }
            if (types.size()!=1) throw bad("Join cannot connect feeding to return");
            if (!seen.add(Set.copyOf(members))) throw bad("Duplicate join");
            result.add(List.copyOf(members));
        }
        return List.copyOf(result);
    }

    public static Result compile(String installationId, Map<String, Object> spec,
                                 Map<String, Object> conductorTypes, Map<String, String> externalNodes,
                                 String busPosition) {
        var ids = terminalIds(spec);
        List<SwitchGearTerminal> terminals = new ArrayList<>();
        Set<String> used = new HashSet<>(ids.values());
        Map<String,String> nodeById = new HashMap<>();
        for (String role : ids.keySet()) {
            String node = externalNodes.get(role), id=ids.get(role);
            if (node == null) throw bad("Missing external terminal node: " + role);
            String previous=nodeById.putIfAbsent(id,node);
            if (previous!=null && !previous.equals(node)) throw bad("Terminal maps to different physical nodes: " + id);
            if (previous==null) terminals.add(new SwitchGearTerminal(id,node,connectionType(role)));
        }
        Map<String,String> sourceIds=spec.containsKey("source_terminals") ? stringMap(map(spec.get("source_terminals")))
                : Map.of("FEEDING","POS","RETURN","NEG");
        if (!sourceIds.keySet().equals(Set.of("FEEDING","RETURN"))) throw bad("source_terminals requires FEEDING and RETURN");
        Map<String,Object> buses=new LinkedHashMap<>();
        for (var entry:sourceIds.entrySet()) {
            identifier(entry.getValue());
            if (!nodeById.containsKey(entry.getValue())) buses.put(entry.getValue(),entry.getKey());
        }
        if (spec.containsKey("busbars")) {
            for (var entry : map(spec.get("busbars")).entrySet()) {
                if (buses.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw bad("Busbar duplicates a source connection point: " + entry.getKey());
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
        Map<ConnectionType,String> sourceNodes=new EnumMap<>(ConnectionType.class);
        for (ConnectionType type : ConnectionType.values()) {
            var pole=terminals.stream().filter(t->t.id().equals(sourceIds.get(type.name()))).findFirst().orElseThrow();
            if (pole.connectionType()!=type) throw bad("Source terminal polarity mismatch: "+pole.id());
            sourceNodes.put(type,pole.nodeId());
            Set<String> seen = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>(); queue.add(sourceIds.get(type.name()));
            while (!queue.isEmpty()) { String t = queue.removeFirst(); if (seen.add(t)) queue.addAll(graph.get(t)); }
            for (var terminal : terminals) if (terminal.connectionType() == type && !seen.contains(terminal.id()))
                throw bad("Terminal has no cable path to rectifier: " + terminal.id());
        }
        return new Result(gear, Collections.unmodifiableMap(positions), Collections.unmodifiableMap(sourceNodes));
    }

    public static Map<String, Object> generated(WiredSwitchGear gear) {
        List<Map<String, Object>> terminals = new ArrayList<>(), cables = new ArrayList<>();
        gear.terminals().forEach(t -> terminals.add(Map.of("id", t.id(), "node_id", t.nodeId(), "connection_type", t.connectionType().name())));
        gear.cables().forEach(c -> cables.add(Map.of("id", c.id(), "from", c.fromTerminalId(), "to", c.toTerminalId(),
                "length_m", c.lengthM(), "conductor_type", c.conductorType().id(),
                "resistance_ohm_per_km", c.conductorType().resistanceOhmPerKm(), "multiplicity", c.multiplicity())));
        return Map.of("installation_id", gear.installationId(), "terminals", terminals, "cables", cables);
    }
    private static Map<String,String> stringMap(Map<String,Object> input) {
        Map<String,String> result=new TreeMap<>();input.keySet().forEach(key->result.put(key,text(input,key)));return result;
    }
    private static void fields(Map<String, Object> map, Set<String> allowed, String label) {
        for (String key : map.keySet()) if (!allowed.contains(key)) throw bad("Unknown " + label + " field: " + key);
    }
    private static void identifier(String s) { if (!s.matches("[\\p{L}\\p{Nd}_-]+")) throw bad("Invalid terminal/cable identifier: " + s); }
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
