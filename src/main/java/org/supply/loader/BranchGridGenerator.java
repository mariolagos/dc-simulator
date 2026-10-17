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
        if (!input.hasPath("branches")) throw bad("Missing branches");
        for (String legacy : List.of("electricalAssociations", "nodes", "lines", "points", "links",
                "power_installations", "installation_connections", "electricalBranches")) {
            if (input.hasPath(legacy)) throw bad("Branch input cannot contain " + legacy);
        }
        Map<String, Object> source = input.getConfig("electricalObjects").root().unwrapped();
        Map<String, Object> objects = new LinkedHashMap<>();
        Map<String, Map<String, String>> addresses = new TreeMap<>();
        List<Map<String, Object>> associations = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String name : new TreeSet<>(source.keySet())) {
            identifier(name);
            Map<String, Object> object = map(source.get(name), name);
            String kind = text(object.get("kind"), name + ".kind");
            if (kind.equals("JUNCTION")) {
                checkFields(object, Set.of("kind", "positions"), name);
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
                validatePositions(map(object.get("positions"), name + ".positions"), null, name);
                if (object.containsKey("id")) throw bad("Use the object name instead of id in branch input: " + name);
                objects.put(name, new LinkedHashMap<>(object));
            } else throw bad("Branch input supports POINT, SUBSTATION and JUNCTION: " + name);
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
            for (Object entry : list) {
                String name;
                String in = "L", out = "R";
                if (entry instanceof String string) name = string;
                else {
                    Map<String, Object> endpoint = map(entry, branch + " endpoint");
                    checkFields(endpoint, Set.of("object", "in", "out"), branch);
                    name = text(endpoint.get("object"), "endpoint.object");
                    if (endpoint.containsKey("in")) in = side(endpoint.get("in"));
                    if (endpoint.containsKey("out")) out = side(endpoint.get("out"));
                    if (in.equals(out)) throw bad("Incoming and outgoing station sides must differ: " + name);
                    if (!source.containsKey(name) || !"SUBSTATION".equals(map(source.get(name), name).get("kind")))
                        throw bad("Explicit sides require a SUBSTATION: " + name);
                }
                if (!source.containsKey(name)) throw bad("Unknown object " + name + " in branch " + branch);
                used.add(name);
                Map<String, Object> original = map(source.get(name), name);
                String resolved = name;
                boolean station = "SUBSTATION".equals(original.get("kind"));
                if (addresses.containsKey(name)) {
                    if (section == null) throw bad("Branch " + branch + " must specify section when using junction " + name);
                    resolved = addresses.get(name).get(section);
                    if (resolved == null) throw bad("Junction " + name + " has no position for section " + section);
                    usedJunctionSections.computeIfAbsent(name, k -> new HashSet<>()).add(section);
                } else validatePositions(map(original.get("positions"), name), section, name);
                ends.add(new Endpoint(name, resolved, station, in, out));
            }
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
            // Star links retain separate railway positions, as in existing section-transition lines.
            String hubSection = junction.getValue().keySet().stream().sorted(
                    Comparator.<String>comparingInt(s -> sectionJunctionCounts.getOrDefault(s, 0))
                            .reversed().thenComparing(Comparator.naturalOrder())).findFirst().orElseThrow();
            List<String> points = new ArrayList<>();
            points.add(junction.getValue().get(hubSection));
            junction.getValue().forEach((section, point) -> { if (!section.equals(hubSection)) points.add(point); });
            for (int i = 1; i < points.size(); i++) {
                String line = "J__" + name + "__" + i;
                addLine(objects, source, lineIds, line, Map.of("kind", "LINE",
                        "feeding_resistance_ohm_per_m", JUNCTION_RESISTANCE,
                        "return_resistance_ohm_per_m", JUNCTION_RESISTANCE));
                associations.add(link(points.get(0), line + ".L"));
                associations.add(link(line + ".R", points.get(i)));
            }
        }
        Set<String> unused = new TreeSet<>(source.keySet());
        unused.removeAll(used);
        if (!unused.isEmpty()) throw bad("Objects not used by any branch: " + unused);
        Map<String, Object> result = new LinkedHashMap<>(input.root().unwrapped());
        result.remove("branches");
        result.put("electricalObjects", objects);
        result.put("electricalAssociations", associations);
        if (result.containsKey("reference_node_id")) {
            String reference = text(result.get("reference_node_id"), "reference_node_id");
            for (String name : addresses.keySet()) {
                if (reference.equals(name) || reference.startsWith(name + "."))
                    throw bad("Reference must name a positioned point/substation, not a junction: " + reference);
            }
        }
        return ConfigFactory.parseMap(result);
    }

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
    private static String side(Object value) {
        String s = text(value, "station side");
        if (!Set.of("L", "R").contains(s)) throw bad("Station side must be L or R");
        return s;
    }
    private static void identifier(String name) {
        if (!name.matches("[A-Za-z0-9_-]+")) throw bad("Invalid name: " + name);
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
}
