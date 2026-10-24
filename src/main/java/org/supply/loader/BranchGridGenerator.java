package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.*;

/** Generates ordinary electrical objects and associations from positioned branch chains. */
public final class BranchGridGenerator {
    private static final double JUNCTION_RESISTANCE = 1e-15;
    private BranchGridGenerator() { }

    /** Accepts a grid configuration, preserves parameters/types, and never modifies the input. */
    public static Config generate(Config input) {
        input = SwitchGearTypeExpander.expand(input);
        if (!input.hasPath("branches")) throw bad("Missing branches");
        for (String legacy : List.of("electricalAssociations", "nodes", "lines", "points", "links",
                "power_installations", "installation_connections", "electricalBranches")) {
            if (input.hasPath(legacy)) throw bad("Branch input cannot contain " + legacy);
        }
        Map<String, Object> source = input.getConfig("electricalObjects").root().unwrapped();
        Map<String, Object> objects = new LinkedHashMap<>();
        Map<String, Map<String, String>> addresses = new TreeMap<>();
        Map<String, String> stationAtJunction = new TreeMap<>();
        List<Map<String, Object>> associations = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String name : new TreeSet<>(source.keySet())) {
            identifier(name);
            Map<String, Object> object = map(source.get(name), name);
            String kind = text(object.get("kind"), name + ".kind");
            if (kind.equals("JUNCTION")) {
                checkFields(object, Set.of("kind", "positions", "conductors"), name);
                Map<String, Object> positions = map(object.get("positions"), name + ".positions");
                if (positions.size() < 2) throw bad("Junction needs at least two section positions: " + name);
                Map<String, String> bySection = new TreeMap<>();
                for (String section : new TreeSet<>(positions.keySet())) {
                    identifier(section);
                    Map<String, Object> pair = map(positions.get(section), name + "." + section);
                    validatePositions(pair, section, name);
                    String point = name + "__" + section;
                    addObject(objects, source, point, Map.of("kind", "POINT", "positions", pair));
                    bySection.put(section, point);
                }
                addresses.put(name, bySection);
            } else if (kind.equals("POINT") || kind.equals("SUBSTATION")) {
                if (object.containsKey("positions")) validatePositions(map(object.get("positions"), name + ".positions"), null, name);
                if (object.containsKey("orientation")) throw bad("Use explicit terminal mapping instead of orientation: " + name);
                if (object.containsKey("id")) throw bad("Use the object name instead of id in branch input: " + name);
                objects.put(name, new LinkedHashMap<>(object));
            } else throw bad("Branch input supports POINT, SUBSTATION and JUNCTION: " + name);
        }
        for (var entry : source.entrySet()) {
            var station = map(entry.getValue(), entry.getKey());
            if (!station.containsKey("junction")) continue;
            String name = entry.getKey(), junction = text(station.get("junction"), name + ".junction");
            if (!"SUBSTATION".equals(station.get("kind")) || !addresses.containsKey(junction))
                throw bad("junction requires a SUBSTATION and a known positioned JUNCTION: " + name);
            if (station.containsKey("positions") || station.containsKey("ports"))
                throw bad("Junction supplies the station port positions; do not specify positions/ports: " + name);
            var gear = map(station.get("switchgear"), name + ".switchgear");
            if (stationAtJunction.putIfAbsent(junction,name) != null) throw bad("Only one station per junction is supported: " + junction);
            var spec = map(objects.get(name), name);
            var positions = map(map(source.get(junction),junction).get("positions"),junction + ".positions");
            spec.remove("junction");spec.remove("positions");spec.put("ports",positions);objects.put(name,spec);
            Set<String> portNames=new HashSet<>();
            SwitchGearCompiler.terminalIds(gear).keySet().forEach(role->portNames.add(role.substring(0,role.indexOf('.'))));
            if (!positions.keySet().equals(portNames)) throw bad("Junction positions and terminal ports must match: " + name);
            var junctionSpec=map(source.get(junction),junction);
            if (!junctionSpec.containsKey("conductors")) throw bad("Specify junction conductors explicitly at a station: " + junction);
            for (String section : positions.keySet()) associations.add(link(name + "." + section,addresses.get(junction).get(section)));
        }
        Set<String> lineIds = new HashSet<>();
        Map<String, Set<String>> usedJunctionSections = new TreeMap<>();
        Map<String, Integer> sectionJunctionCounts = new TreeMap<>();
        Map<String, Object> branches = input.getConfig("branches").root().unwrapped();
        if (branches.isEmpty()) throw bad("At least one branch is required");
        for (String branch : new TreeSet<>(branches.keySet())) {
            identifier(branch);
            Object value = branches.get(branch);
            String section = branch.matches("[0-9]+") ? branch : null;
            String type = "standard";
            Object chain = value;
            if (value instanceof Map<?, ?>) {
                Map<String, Object> spec = map(value, branch);
                checkFields(spec, Set.of("section", "objects", "lineType"), branch);
                section = spec.containsKey("section") ? text(spec.get("section"), branch + ".section") : section;
                if (spec.containsKey("lineType")) type = text(spec.get("lineType"), branch + ".lineType");
                chain = spec.get("objects");
            }
            if (!(chain instanceof List<?> list) || list.size() < 2)
                throw bad("Branch needs at least two objects: " + branch);
            if (!input.hasPath("lineTypes." + type)) throw bad("Unknown line type: " + type);
            List<Endpoint> ends = new ArrayList<>();
            for (int entryIndex=0;entryIndex<list.size();entryIndex++) {
                Object entry=list.get(entryIndex);
                String name;
                String in = "L", out = "R";
                if (entry instanceof String string) { name = string; identifier(name); }
                else {
                    Map<String, Object> endpoint = map(entry, branch + " endpoint");
                    checkFields(endpoint, Set.of("object", "in", "out"), branch);
                    name = text(endpoint.get("object"), "endpoint.object");
                    if (endpoint.containsKey("in")) in = text(endpoint.get("in"),"in");
                    if (endpoint.containsKey("out")) out = text(endpoint.get("out"),"out");
                    if (in.equals(out)) throw bad("Incoming and outgoing station sides must differ: " + name);
                    if (!source.containsKey(name) || !"SUBSTATION".equals(map(source.get(name), name).get("kind")))
                        throw bad("Explicit sides require a SUBSTATION: " + name);
                }
                if (!source.containsKey(name)) throw bad("Unknown object " + name + " in branch " + branch);
                if (originalHasJunction(source,name)) throw bad("Use junction " + map(source.get(name),name).get("junction") + " in the branch chain, not its attached station " + name);
                used.add(name);
                Map<String, Object> original = map(source.get(name), name);
                String resolved = name;
                boolean station = "SUBSTATION".equals(original.get("kind"));
                if (addresses.containsKey(name)) {
                    if (section == null) throw bad("Branch " + branch + " must specify section when using junction " + name);
                    resolved = addresses.get(name).get(section);
                    if (resolved == null) throw bad("Junction " + name + " has no position for section " + section);
                    boolean firstSectionUse = usedJunctionSections.computeIfAbsent(name, k -> new HashSet<>()).add(section);
                    if (!firstSectionUse && stationAtJunction.containsKey(name))
                        throw bad("Junction station requires one branch endpoint per section: " + name + "." + section);
                } else if (station && original.containsKey("ports")) {
                    var ports=map(original.get("ports"),name+".ports");
                    for (String port : entryIndex==0 ? List.of(out) : entryIndex==list.size()-1 ? List.of(in) : List.of(in,out)) {
                        if (!ports.containsKey(port)) throw bad("Unknown station port in branch: "+name+"."+port);
                        validatePositions(map(ports.get(port),port),section,name);
                    }
                } else validatePositions(map(original.get("positions"), name), section, name);
                ends.add(new Endpoint(name, resolved, station, in, out));
            }
            for (int i = 1; i + 1 < ends.size(); i++) if (stationAtJunction.containsKey(ends.get(i).name))
                throw bad("Junction station must occur at a branch endpoint: " + ends.get(i).name);
            int junctionCount = (int) ends.stream().filter(e -> addresses.containsKey(e.name)).count();
            if (section != null) sectionJunctionCounts.merge(section, junctionCount, Math::max);
            for (int i = 0; i + 1 < ends.size(); i++) {
                Endpoint a = ends.get(i), b = ends.get(i + 1);
                if (a.name.equals(b.name)) throw bad("Adjacent repeated object in branch " + branch + ": " + a.name);
                String line = "B__" + branch + "__" + (i + 1);
                addLine(objects, source, lineIds, line, Map.of("kind", "LINE", "type", type));
                associations.add(link(a.port(false), line + ".L"));
                associations.add(link(line + ".R", b.port(true)));
            }
        }
        for (var junction : addresses.entrySet()) {
            String name = junction.getKey();
            if (!junction.getValue().keySet().equals(usedJunctionSections.getOrDefault(name, Set.of())))
                throw bad("Every junction section must be used by a branch: " + name);
            boolean owned = stationAtJunction.containsKey(name);
            if (owned) used.add(stationAtJunction.get(name));
            // Rail continuity comes from the declared junction conductors, not the station type.
            var junctionSpec=map(source.get(name),name);
            Object conductors=junctionSpec.getOrDefault("conductors",List.of("N.F","S.F","N.R","S.R"));
            if (!(conductors instanceof List<?> roles) || new HashSet<>(roles).size()!=roles.size()
                    || !List.of("N.F","S.F","N.R","S.R").containsAll(roles)) throw bad("Invalid junction conductors: "+name);
            String hubSection = junction.getValue().keySet().stream().sorted(
                    Comparator.<String>comparingInt(s -> sectionJunctionCounts.getOrDefault(s, 0))
                            .reversed().thenComparing(Comparator.naturalOrder())).findFirst().orElseThrow();
            List<String> points = new ArrayList<>();
            points.add(junction.getValue().get(hubSection));
            junction.getValue().forEach((section, point) -> { if (!section.equals(hubSection)) points.add(point); });
            for (int i = 1; i < points.size() && !((List<?>)conductors).isEmpty(); i++) {
                String line = "J__" + name + "__" + i;
                Map<String,Object> spec = new LinkedHashMap<>(Map.of("kind", "LINE",
                        "feeding_resistance_ohm_per_m", JUNCTION_RESISTANCE,
                        "return_resistance_ohm_per_m", JUNCTION_RESISTANCE));
                spec.put("conductors",conductors);
                addLine(objects, source, lineIds, line, spec);
                for (Object value : (List<?>) conductors) {
                    String role=text(value,"junction conductor");
                    if (!Set.of("N.F","S.F","N.R","S.R").contains(role)) throw bad("Unknown junction conductor: "+role);
                    associations.add(link(points.get(0)+"."+role,line+".L."+role));
                    associations.add(link(line+".R."+role,points.get(i)+"."+role));
                }
            }
        }
        Set<String> unused = new TreeSet<>(source.keySet());
        unused.removeAll(used);
        if (!unused.isEmpty()) throw bad("Objects not used by any branch: " + unused);
        Map<String, Object> result = new LinkedHashMap<>(input.root().unwrapped());
        result.remove("branches");
        result.remove("routeDefinitions");
        result.put("electricalObjects", objects);
        result.put("electricalAssociations", associations);
        if (result.containsKey("reference_node_id")) {
            String reference = text(result.get("reference_node_id"), "reference_node_id");
            for (String name : addresses.keySet()) {
                if (reference.equals(name) || reference.startsWith(name + "."))
                    throw bad("Reference must name a positioned point/substation, not a junction: " + reference);
            }
        }
        return com.typesafe.config.ConfigValueFactory.fromMap(result).toConfig();
    }

    private static boolean originalHasJunction(Map<String,Object> source,String name) { return map(source.get(name),name).containsKey("junction"); }

    private record Endpoint(String name, String resolved, boolean station, String in, String out) {
        String port(boolean incoming) { return resolved + (station ? "." + (incoming ? in : out) : ""); }
    }
    private static void validatePositions(Map<String, Object> positions, String section, String object) {
        if (!positions.keySet().equals(Set.of("N", "S"))) throw bad("Positions require N and S: " + object);
        for (String track : List.of("N", "S")) {
            String coordinate = text(positions.get(track), object + "." + track).trim();
            String[] fields = coordinate.split("\\s+");
            if (fields.length != 3 || !fields[1].matches("[0-9]+\\+[0-9]+") || !fields[2].equals(track))
                throw bad("Invalid position for " + object + "." + track + ": " + coordinate);
            if (section != null && !fields[0].equals(section))
                throw bad("Object " + object + " is not positioned in branch section " + section + ": " + coordinate);
        }
    }
    private static void addObject(Map<String, Object> objects, Map<String, Object> source, String name, Object value) {
        if (source.containsKey(name) || objects.putIfAbsent(name, value) != null)
            throw bad("Generated object name collides: " + name);
    }
    private static void addLine(Map<String, Object> objects, Map<String, Object> source, Set<String> ids,
                                String name, Object value) {
        if (!ids.add(name)) throw bad("Duplicate generated line: " + name);
        addObject(objects, source, name, value);
    }
    private static Map<String, Object> link(String from, String to) { return Map.of("from", from, "to", to); }
    private static Map<String, Object> map(Object value, String context) {
        if (!(value instanceof Map<?, ?> raw)) throw bad("Expected object: " + context);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put((String) key, item));
        return result;
    }
    private static void checkFields(Map<String, Object> map, Set<String> allowed, String context) {
        for (String key : map.keySet()) if (!allowed.contains(key)) throw bad("Unknown field " + key + " in " + context);
    }
    private static String text(Object value, String context) {
        if (!(value instanceof String s) || s.isBlank()) throw bad("Expected nonempty string: " + context);
        return s;
    }
    private static void identifier(String name) {
        if (!name.matches("[A-Za-z0-9_-]+")) throw bad("Invalid name: " + name);
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
}
