package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.*;
import org.supply.model.GridModel;
import java.util.*;

/** Generates electrical route lists using branch order and the actual generated junction wiring. */
public final class BranchRouteGenerator {
    private BranchRouteGenerator() { }
    private record Step(String branch, boolean reverse, int lineCount) { }
    private record Edge(String to, String lineId) { }

    public static Map<String,Object> generate(Config branchGrid, GridModel model) {
        if (!branchGrid.hasPath("routeDefinitions")) return Map.of();
        Config definitions = branchGrid.getConfig("routeDefinitions");
        if (definitions.isEmpty()) throw bad("routeDefinitions must contain at least one route");
        Config branches = branchGrid.getConfig("branches");
        Map<String,Line> lines = new HashMap<>();
        for (Line line : model.getLines()) if (lines.putIfAbsent(line.getLineId(),line) != null)
            throw bad("Duplicate line id: " + line.getLineId());
        var internal = new Components(model);
        Map<String,Object> routes = new TreeMap<>();
        for (String route : new TreeSet<>(definitions.root().keySet())) {
            Config spec = definitions.getConfig(route);
            for (String key : spec.root().keySet()) if (!Set.of("branches","track").contains(key))
                throw bad("Unknown route field: " + route + "." + key);
            String track = spec.getString("track");
            if (!Set.of("N","S").contains(track)) throw bad("Route track must be N or S: " + route);
            List<String> references = spec.getStringList("branches");
            if (references.isEmpty()) throw bad("Route needs branches: " + route);
            List<Step> steps = new ArrayList<>();
            for (String reference : references) {
                boolean reverse = !branches.root().containsKey(reference) && reference.startsWith("-");
                String name = reverse ? reference.substring(1) : reference;
                if (!branches.root().containsKey(name)) throw bad("Unknown branch in route " + route + ": " + reference);
                Object value = branches.getValue(name).unwrapped();
                if (value instanceof Map<?,?> map) value = map.get("objects");
                if (!(value instanceof List<?> chain) || chain.size() < 2) throw bad("Invalid branch: " + name);
                steps.add(new Step(name,reverse,chain.size()-1));
            }
            Map<String,Object> result = new LinkedHashMap<>();
            for (String kind : List.of("F","R")) {
                Map<String,List<Edge>> junctions = junctionGraph(model,internal,track,kind,true);
                Map<String,List<Edge>> physicalJunctions = junctionGraph(model,internal,track,kind,false);
                List<String> selected = new ArrayList<>(); String previousEnd = null;
                for (Step step : steps) {
                    for (int j = 0; j < step.lineCount(); j++) {
                        int i = step.reverse() ? step.lineCount()-j : j+1;
                        String id = "B__" + step.branch() + "__" + i + "." + track + "." + kind;
                        Line line = lines.get(id);
                        if (line == null) throw bad("Missing generated line for route " + route + ": " + id);
                        String start = step.reverse() ? line.getToNode().getNodeId() : line.getFromNode().getNodeId();
                        String end = step.reverse() ? line.getFromNode().getNodeId() : line.getToNode().getNodeId();
                        if (previousEnd != null) selected.addAll(connectWithPhysicalJunction(route,previousEnd,start,physicalJunctions,junctions,internal));
                        selected.add(id); previousEnd = end;
                    }
                }
                if (new HashSet<>(selected).size() != selected.size()) throw bad("Repeated line in route " + route + "; loops are not supported");
                result.put(kind.equals("F") ? "feeding_lines" : "return_lines",List.copyOf(selected));
            }
            routes.put(route,result);
        }
        return Collections.unmodifiableMap(routes);
    }

    private static Map<String,List<Edge>> junctionGraph(GridModel model, Components internal, String track, String kind, boolean collapse) {
        Map<String,List<Edge>> graph = new TreeMap<>();
        model.getLines().stream().sorted(Comparator.comparing(Line::getLineId))
                .filter(l -> l.getLineId().startsWith("J__") && l.getLineId().endsWith("."+track+"."+kind))
                .forEach(l -> {
                    String a=l.getFromNode().getNodeId(), b=l.getToNode().getNodeId();
                    if(collapse) { a=internal.root(a);b=internal.root(b); }
                    graph.computeIfAbsent(a,k->new ArrayList<>()).add(new Edge(b,l.getLineId()));
                    graph.computeIfAbsent(b,k->new ArrayList<>()).add(new Edge(a,l.getLineId()));
                });
        return graph;
    }
    private static List<String> connectWithPhysicalJunction(String route, String from, String to,
                                                            Map<String,List<Edge>> physical, Map<String,List<Edge>> collapsed, Components internal) {
        if (from.equals(to)) return List.of();
        // Retain the actual rail-junction traversal even when station return cables
        // also connect its endpoints through the common return busbar.
        List<List<String>> paths = new ArrayList<>();
        search(from,to,physical,new HashSet<>(),new ArrayList<>(),paths);
        if (paths.size()>1) throw bad("Route " + route + ": ambiguous physical junction connection");
        if (paths.size()==1) return paths.get(0);
        return connect(route,internal.root(from),internal.root(to),collapsed);
    }

    private static List<String> connect(String route, String from, String to, Map<String,List<Edge>> graph) {
        if (from.equals(to)) return List.of();
        List<List<String>> paths = new ArrayList<>();
        search(from,to,graph,new HashSet<>(),new ArrayList<>(),paths);
        if (paths.isEmpty()) throw bad("Route " + route + ": branches do not connect in the specified order/direction: " + from + " -> " + to);
        if (paths.size()>1) throw bad("Route " + route + ": ambiguous junction connection: " + from + " -> " + to);
        return paths.get(0);
    }
    private static void search(String current, String target, Map<String,List<Edge>> graph, Set<String> seen,
                               List<String> path, List<List<String>> paths) {
        if (paths.size()>1) return;
        if (current.equals(target)) { paths.add(List.copyOf(path)); return; }
        seen.add(current);
        for (Edge edge : graph.getOrDefault(current,List.of())) if (!seen.contains(edge.to())) {
            path.add(edge.lineId()); search(edge.to(),target,graph,seen,path,paths); path.remove(path.size()-1);
        }
        seen.remove(current);
    }
    private static final class Components {
        private final Map<String,String> parent = new HashMap<>();
        Components(GridModel model) {
            model.getNodes().forEach(n -> parent.put(n.getNodeId(),n.getNodeId()));
            for (var station : model.getSixTerminalRectifierInstallations()) {
                var terminals=station.terminalNodeIds();
                for (var type : ConnectionType.values()) {
                    String first=null;
                    for (var entry : terminals.entrySet()) if (entry.getKey().connectionType()==type) {
                        if (first==null) first=entry.getValue(); else join(first,entry.getValue());
                    }
                }
            }
            for (var gear : model.getSwitchGears()) {
                Map<String,String> nodes=new HashMap<>();gear.terminals().forEach(t->nodes.put(t.id(),t.nodeId()));
                gear.cables().forEach(c->join(nodes.get(c.fromTerminalId()),nodes.get(c.toTerminalId())));
            }
        }
        String root(String id) {
            if (!parent.containsKey(id)) throw bad("Unknown electrical node: " + id);
            String p=parent.get(id);while(!p.equals(parent.get(p))) p=parent.get(p);return p;
        }
        void join(String a,String b) { String x=root(a),y=root(b);if(!x.equals(y)) parent.put(x,y); }
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
}
