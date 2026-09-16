package org.supply.solver.build;

import org.supply.domain.Route;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.DiodeSubstationElement;
import org.supply.solver.model.ElectricalElement;
import org.supply.solver.model.FixedLoadElement;
import org.supply.solver.model.ThyristorSubstationElement;

import java.util.*;

/** Uses the built network, including internal terminal connectors, not railway proximity. */
public final class ElectricalRouteCheck {
    private ElectricalRouteCheck() { }

    public record Result(List<String> errors, List<String> warnings, String topology) { }

    public static Result inspect(CalculationNetwork network, List<Route> routes,
                                 String selectedRouteId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        Map<String, CalculationNode> nodes = new LinkedHashMap<>();
        for (CalculationNode node : network.nodes()) {
            if (nodes.putIfAbsent(node.id(), node) != null) {
                errors.add("Duplicate calculation node: " + node.id());
            }
            if (!Double.isFinite(node.positionM())) {
                errors.add("Non-finite model position for node " + node.id());
            }
        }

        for (ElectricalElement element : network.elements()) {
            String id;
            String feeding;
            String returning;
            if (element instanceof DiodeSubstationElement station) {
                id = station.id(); feeding = station.feedingNodeId(); returning = station.returnNodeId();
                checkStationValues(id, station.emfV().asDouble(),
                        station.internalResistanceOhm().asDouble(), errors);
            } else if (element instanceof ThyristorSubstationElement station) {
                id = station.id(); feeding = station.feedingNodeId(); returning = station.returnNodeId();
                checkStationValues(id, station.emfV().asDouble(),
                        station.internalResistanceOhm().asDouble(), errors);
            } else if (element instanceof FixedLoadElement load) {
                id = load.id(); feeding = load.feedingNodeId(); returning = load.returnNodeId();
                if (!Double.isFinite(load.powerW())) {
                    errors.add("Non-finite fixed load power: " + id);
                }
            } else {
                continue;
            }
            if (!nodes.containsKey(feeding) || !nodes.containsKey(returning)) {
                errors.add("Installation " + id + ": unknown feeding/return node");
            }
            if (Objects.equals(feeding, returning)) {
                errors.add("Installation " + id + ": feeding and return use the same node");
            }
        }
        Map<String, List<CalculationBranch>> byLine = new LinkedHashMap<>();
        Map<String, Set<String>> internal = new LinkedHashMap<>();
        Set<String> branchIds = new HashSet<>();
        for (CalculationBranch branch : network.branches()) {
            if (!branchIds.add(branch.id())) {
                errors.add("Duplicate calculation branch: " + branch.id());
            }
            if (!nodes.containsKey(branch.fromNodeId()) || !nodes.containsKey(branch.toNodeId())) {
                errors.add("Unknown endpoint for branch " + branch.id());
            }
            if (Objects.equals(branch.fromNodeId(), branch.toNodeId())) {
                errors.add("Branch connects a node to itself: " + branch.id());
            }
            double resistance = branch.resistanceOhm().asDouble();
            if (!Double.isFinite(resistance) || resistance <= 0.0) {
                errors.add("Invalid resistance for branch " + branch.id() + ": " + resistance);
            }
            if (branch.sourceId() == null || branch.sourceId().isBlank()) {
                errors.add("Missing source line ID for branch " + branch.id());
            } else if (branch.sourceId().startsWith("internal_")) {
                link(internal, branch.fromNodeId(), branch.toNodeId());
            } else {
                byLine.computeIfAbsent(branch.sourceId(), ignored -> new ArrayList<>()).add(branch);
            }
        }

        Set<String> routeIds = new HashSet<>();
        List<Route> ordered = new ArrayList<>(routes);
        ordered.sort(Comparator.comparing(Route::id));
        for (Route route : ordered) {
            if (!routeIds.add(route.id())) {
                errors.add("Duplicate route ID: " + route.id());
            }
            checkPath(route.id(), "FEEDING", route.feedingLineIds(), byLine, internal, errors, warnings);
            checkPath(route.id(), "RETURN", route.returnLineIds(), byLine, internal, errors, warnings);
            Set<String> feedingNodes = pathNodes(route.feedingLineIds(), byLine);
            Set<String> returnNodes = pathNodes(route.returnLineIds(), byLine);
            if (connected(feedingNodes, returnNodes, internal)) {
                errors.add("Route " + route.id() + ": FEEDING and RETURN are electrically joined");
            }
            if (selectedRouteId == null || selectedRouteId.equals(route.id())) {
                out.append("\nROUTE ").append(route.id()).append('\n');
                printPath(out, "FEEDING", route.feedingLineIds(), byLine, nodes);
                printPath(out, "RETURN", route.returnLineIds(), byLine, nodes);
                out.append("Internal terminal connections touching the route:\n");
                Set<String> selectedNodes = new HashSet<>(feedingNodes);
                selectedNodes.addAll(returnNodes);
                for (CalculationBranch branch : network.branches()) {
                    if (branch.sourceId() != null && branch.sourceId().startsWith("internal_")
                            && connected(selectedNodes,
                            new HashSet<>(List.of(branch.fromNodeId(), branch.toNodeId())), internal)) {
                        out.append("  ").append(branch.sourceId()).append(": ")
                                .append(branch.fromNodeId()).append(" <-> ").append(branch.toNodeId())
                                .append(" R_ohm=").append(branch.resistanceOhm().asDouble()).append('\n');
                    }
                }
                out.append("Installations directly attached (including internal terminal connections):\n");
                int installationCount = 0;
                for (ElectricalElement element : network.elements()) {
                    String id;
                    String from;
                    String to;
                    String description;
                    if (element instanceof DiodeSubstationElement station) {
                        id = station.id(); from = station.feedingNodeId(); to = station.returnNodeId();
                        description = "DIODE enabled=" + station.enabled();
                    } else if (element instanceof ThyristorSubstationElement station) {
                        id = station.id(); from = station.feedingNodeId(); to = station.returnNodeId();
                        description = "THYRISTOR enabled=" + station.enabled();
                    } else if (element instanceof FixedLoadElement load) {
                        id = load.id(); from = load.feedingNodeId(); to = load.returnNodeId();
                        description = "FIXED_LOAD power_W=" + load.powerW();
                    } else {
                        continue;
                    }
                    boolean feedingAttached = connected(feedingNodes, Set.of(from), internal);
                    boolean returnAttached = connected(returnNodes, Set.of(to), internal);
                    if (feedingAttached || returnAttached) {
                        installationCount++;
                        out.append("  ").append(id).append(' ').append(description)
                                .append(" FEEDING=").append(from).append(" RETURN=").append(to)
                                .append(" route_feeding=").append(feedingAttached)
                                .append(" route_return=").append(returnAttached).append('\n');
                    }
                }
                if (installationCount == 0) {
                    out.append("  none directly attached; feeding through other network lines is not shown here\n");
                }
            }
        }
        if (routes.isEmpty()) {
            warnings.add("No explicit electrical routes: route topology cannot be checked");
        }
        if (selectedRouteId != null && !routeIds.contains(selectedRouteId)) {
            errors.add("Unknown selected route ID: " + selectedRouteId);
        }
        return new Result(List.copyOf(errors), List.copyOf(warnings), out.toString());
    }

    private static void checkPath(String routeId, String side, List<String> lineIds,
                                  Map<String, List<CalculationBranch>> byLine,
                                  Map<String, Set<String>> internal,
                                  List<String> errors, List<String> warnings) {
        if (lineIds.isEmpty()) {
            errors.add("Route " + routeId + ": empty " + side + " path");
        }
        Set<String> seen = new HashSet<>();
        for (String lineId : lineIds) {
            if (!seen.add(lineId)) {
                warnings.add("Route " + routeId + " " + side + ": repeated line " + lineId);
            }
            List<CalculationBranch> branches = byLine.get(lineId);
            if (branches == null) {
                errors.add("Route " + routeId + " " + side + ": unknown line " + lineId);
                continue;
            }
            String prefix = "FEEDING".equals(side) ? "F" : "R";
            for (CalculationBranch branch : branches) {
                if (!branch.fromNodeId().startsWith(prefix) || !branch.toNodeId().startsWith(prefix)) {
                    errors.add("Route " + routeId + " " + side + " line " + lineId
                            + ": train placement requires both node IDs to start with " + prefix
                            + " (" + branch.fromNodeId() + ", " + branch.toNodeId() + ")");
                }
            }
        }
        for (int i = 1; i < lineIds.size(); i++) {
            String previous = lineIds.get(i - 1);
            String next = lineIds.get(i);
            if (!byLine.containsKey(previous) || !byLine.containsKey(next)) {
                continue;
            }
            if (!connected(pathNodes(List.of(previous), byLine), pathNodes(List.of(next), byLine), internal)) {
                errors.add("Route " + routeId + " " + side + ": gap between "
                        + previous + " and " + next + " (no shared node or internal terminal connection)");
            }
        }
    }

    private static void checkStationValues(String id, double emf, double resistance,
                                           List<String> errors) {
        if (!Double.isFinite(emf) || emf <= 0.0) {
            errors.add("Invalid station emf_V: " + id + " = " + emf);
        }
        if (!Double.isFinite(resistance) || resistance <= 0.0) {
            errors.add("Invalid station internal_resistance_ohm: " + id + " = " + resistance);
        }
    }

    private static void printPath(StringBuilder out, String side, List<String> lines,
                                  Map<String, List<CalculationBranch>> byLine,
                                  Map<String, CalculationNode> nodes) {
        out.append(side).append(" (configured order; line endpoints are undirected):\n");
        for (int i = 0; i < lines.size(); i++) {
            String lineId = lines.get(i);
            out.append("  ").append(i + 1).append(". ").append(lineId).append('\n');
            for (CalculationBranch branch : byLine.getOrDefault(lineId, List.of())) {
                CalculationNode from = nodes.get(branch.fromNodeId());
                CalculationNode to = nodes.get(branch.toNodeId());
                out.append("     ").append(describe(from, branch.fromNodeId()))
                        .append(" <-> ").append(describe(to, branch.toNodeId()))
                        .append(" R_ohm=").append(branch.resistanceOhm().asDouble()).append('\n');
                if (from != null && to != null && Objects.equals(from.sectionId(), to.sectionId())) {
                    out.append("     model_length_m=")
                            .append(Math.abs(to.positionM() - from.positionM())).append('\n');
                }
            }
        }
    }

    private static String describe(CalculationNode node, String id) {
        return node == null ? id + " [UNKNOWN]" : id + " [section=" + node.sectionId()
                + " track=" + node.trackId() + " position_m=" + node.positionM() + "]";
    }

    private static Set<String> pathNodes(List<String> lines,
                                          Map<String, List<CalculationBranch>> byLine) {
        Set<String> result = new LinkedHashSet<>();
        for (String line : lines) {
            for (CalculationBranch branch : byLine.getOrDefault(line, List.of())) {
                result.add(branch.fromNodeId());
                result.add(branch.toNodeId());
            }
        }
        return result;
    }

    private static boolean connected(Set<String> left, Set<String> right,
                                      Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>(left);
        Deque<String> pending = new ArrayDeque<>(left);
        while (!pending.isEmpty()) {
            String node = pending.removeFirst();
            if (right.contains(node)) { return true; }
            for (String neighbor : graph.getOrDefault(node, Set.of())) {
                if (visited.add(neighbor)) { pending.addLast(neighbor); }
            }
        }
        return false;
    }

    private static void link(Map<String, Set<String>> graph, String from, String to) {
        graph.computeIfAbsent(from, ignored -> new LinkedHashSet<>()).add(to);
        graph.computeIfAbsent(to, ignored -> new LinkedHashSet<>()).add(from);
    }
}
