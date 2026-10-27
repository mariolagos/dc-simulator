package org.supply.app;

import com.typesafe.config.Config;
import org.supply.track.LoadedTrackModel;
import org.supply.track.PathSample;
import org.supply.track.RouteView;
import org.supply.track.RwyCoordinate;
import org.supply.track.TrackConfigLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.supply.app.DcReporter.*;

final class GraphicalTimetableMarkerWriter {
    // Axis is derived from route traversal on the base-route axis, never from N/S.
    private record RouteScope(String axis, Map<String, double[]> spans, Set<String> nodes, Set<String> tracks) { }

    static void writeGraphicalTimetableMarkers(DcStudyContext context,
                                               BaseRoutePositionMapper mapper, Path exportDirectory, Path outputPath) throws IOException {
        Map<String, RouteScope> routes = scopes(context, mapper);
        boolean directional = !routes.isEmpty();
        Map<String, StationRange> legacyStations = new LinkedHashMap<>();
        Set<String> emitted = new java.util.LinkedHashSet<>();
        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("name,base_route_position_m,kind" + (directional ? ",axis" : ""));
            writer.newLine();
            Path stations = exportDirectory.resolve("track_stations.csv");
            try (BufferedReader reader = Files.newBufferedReader(stations)) {
                if (!"name,position_rwy,model_position_m".equals(reader.readLine())) {
                    throw new IllegalArgumentException("Unexpected station CSV header: " + stations);
                }
                String line; int row = 1;
                while ((line = reader.readLine()) != null) {
                    row++;
                    if (line.isBlank()) continue;
                    List<String> fields = csvFields(line, row);
                    if (fields.size() != 3) throw new IllegalArgumentException("Invalid station row: " + row);
                    String position = fields.get(1).trim();
                    String section = position.split("\\s+")[0];
                    Double mapped = mapper.map(section, parseDouble(fields.get(2)));
                    if (mapped == null) continue;
                    if (!directional) {
                        legacyStations.computeIfAbsent(fields.get(0), ignored -> new StationRange()).include(mapped);
                    }
                    for (RouteScope route : routes.values()) {
                        if (contains(route, position)) emit(writer, emitted, fields.get(0), mapped, "STATION", route.axis());
                    }
                }
            }
            for (Map.Entry<String, StationRange> station : legacyStations.entrySet()) {
                emit(writer, emitted, station.getKey(), station.getValue().centre(), "STATION", "");
            }
            writeSupplyPoints(writer, emitted, context, mapper, routes);
            String previous = null;
            for (Map.Entry<String, Double> section : mapper.sectionOffsetsM.entrySet()) {
                if (previous != null) emit(writer, emitted, previous + "/" + section.getKey(), section.getValue(), "SECTION_BOUNDARY", directional ? "BOTH" : "");
                previous = section.getKey();
            }
        }
    }

    private static void writeSupplyPoints(BufferedWriter writer, Set<String> emitted,
                                          DcStudyContext context, BaseRoutePositionMapper mapper, Map<String, RouteScope> routes) throws IOException {
        if (!context.scenario().hasPath("dcsim.grid.nodes")
                || !context.scenario().hasPath("dcsim.grid.power_installations")) return;
        Config grid = context.scenario().getConfig("dcsim.grid");
        Map<String, String> nodes = new LinkedHashMap<>();
        for (Config node : grid.getConfigList("nodes")) {
            nodes.put(node.getString("node_id"), node.getString("position_rwy"));
        }
        Map<String, Set<String>> connections = new LinkedHashMap<>();
        if (grid.hasPath("installation_connections")) {
            for (Config c : grid.getConfigList("installation_connections")) {
                connections.computeIfAbsent(c.getString("installation_id"), k -> new HashSet<>()).add(c.getString("node_id"));
            }
        }
        Map<String, String> suffixes = Map.of("NORTH_POWER_LEFT", "A", "NORTH_POWER_RIGHT", "B",
                "SOUTH_POWER_LEFT", "C", "SOUTH_POWER_RIGHT", "D", "NORTH_RETURN", "N", "SOUTH_RETURN", "S");
        for (Config installation : grid.getConfigList("power_installations")) {
            if (!"SUBSTATION".equals(installation.getString("installation_category"))
                    || installation.hasPath("enabled") && !installation.getBoolean("enabled")) continue;
            String id = installation.getString("installation_id");
            Map<String, String> positions = new LinkedHashMap<>();
            for (String node : connections.getOrDefault(id, Set.of())) positions.put(node, nodes.get(node));
            // In explicit switchgear grids, railway line endpoints are station terminals;
            // source POS/NEG nodes are internal and do not themselves belong to routes.
            if (grid.hasPath("switchgears")) {
                for (Config gear : grid.getConfigList("switchgears")) {
                    if (!id.equals(gear.getString("installation_id"))) continue;
                    for (Config terminal : gear.getConfigList("terminals")) {
                        String node = terminal.getString("node_id");
                        if (nodes.containsKey(node)) positions.put(node, nodes.get(node));
                    }
                }
            }
            if (installation.hasPath("terminals")) {
                Config terminals = installation.getConfig("terminals");
                for (String terminal : terminals.root().keySet()) {
                    Object value = terminals.getValue(terminal).unwrapped();
                    if (value instanceof String node) positions.put(node, nodes.get(node));
                    else if (value instanceof Map<?, ?> object && object.get("position_rwy") instanceof String position) {
                        String suffix = suffixes.get(terminal);
                        if (suffix == null) throw new IllegalArgumentException("Unknown terminal: " + terminal);
                        String node = object.get("node_id") instanceof String explicit ? explicit
                                : (terminal.endsWith("RETURN") ? "R_" : "F_") + id + "_" + suffix;
                        positions.put(node, position);
                    }
                }
            }
            if (routes.isEmpty()) {
                StationRange range = new StationRange();
                for (String position : positions.values()) {
                    Double mapped = mapper.mapRailwayPosition(position);
                    if (mapped != null) range.include(mapped);
                }
                if (!range.isEmpty()) emit(writer, emitted, id, range.centre(), "SUPPLY_POINT", "");
            }
            for (RouteScope route : routes.values()) {
                StationRange range = new StationRange();
                for (Map.Entry<String, String> entry : positions.entrySet()) {
                    if (!route.nodes().contains(entry.getKey())) continue;
                    Double mapped = mapper.mapRailwayPosition(entry.getValue());
                    if (mapped != null) range.include(mapped);
                }
                if (!range.isEmpty()) emit(writer, emitted, id, range.centre(), "SUPPLY_POINT", route.axis());
            }
        }
    }

    private static Map<String, RouteScope> scopes(DcStudyContext context, BaseRoutePositionMapper mapper) throws IOException {
        Config scenario = context.scenario();
        Config electricRoutes = scenario.hasPath("dcsim.traffic.routes")
                ? scenario.getConfig("dcsim.traffic.routes")
                : scenario.hasPath("routes") ? scenario.getConfig("routes")
                : scenario.hasPath("dcsim.routes") ? scenario.getConfig("dcsim.routes") : null;
        // Reports without electrical routes retain the established three-column
        // marker format. Do not require trackdata or grid lines in this mode.
        if (electricRoutes == null || electricRoutes.root().isEmpty()) return Map.of();
        LoadedTrackModel track;
        try { track = new TrackConfigLoader().load(context.dcsim(), context.confFile()); }
        catch (Exception e) { throw new IOException("Could not load marker routes", e); }
        Map<String, String> nodePositions = new LinkedHashMap<>();
        for (Config node : scenario.getConfigList("dcsim.grid.nodes")) nodePositions.put(node.getString("node_id"), node.getString("position_rwy"));
        Map<String, Config> lines = new LinkedHashMap<>();
        for (Config line : scenario.getConfigList("dcsim.grid.lines")) lines.put(line.getString("line_id"), line);
        Map<String, RouteScope> result = new LinkedHashMap<>();
        for (RouteView view : track.getRouteViewsById().values()) {
            Map<String, double[]> spans = new LinkedHashMap<>();
            Double first = null, last = null;
            for (PathSample sample : view.getSamples()) {
                RwyCoordinate c = sample.getRailwayCoordinate();
                double[] span = spans.computeIfAbsent(c.getSectionId(), k -> new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY});
                span[0] = Math.min(span[0], c.getPositionM()); span[1] = Math.max(span[1], c.getPositionM());
                Double mapped = mapper.mapRailwayCoordinate(c.getSectionId(), c.getPositionM());
                if (mapped != null) { if (first == null) first = mapped; last = mapped; }
            }
            if (first == null || last == null || first.equals(last)) continue;
            Set<String> routeNodes = new HashSet<>();
            String routeId = view.getRouteId();
            if (electricRoutes != null && electricRoutes.root().containsKey(routeId)) {
                Config electric = electricRoutes.getConfig("\"" + routeId + "\"");
                for (String key : List.of("feeding_lines", "return_lines")) {
                    if (!electric.hasPath(key)) continue;
                    for (String lineId : electric.getStringList(key)) {
                        Config line = lines.get(lineId);
                        if (line == null) throw new IllegalArgumentException("Unknown route line: " + lineId);
                        routeNodes.add(line.getString("node_from_id")); routeNodes.add(line.getString("node_to_id"));
                    }
                }
            }
            Set<String> tracks = new HashSet<>();
            for (String node : routeNodes) {
                String position = nodePositions.get(node);
                if (position != null) {
                    String[] parts = position.trim().split("\\s+");
                    if (parts.length >= 3) tracks.add(parts[2]);
                }
            }
            result.put(routeId, new RouteScope(last > first ? "TOP" : "BOTTOM", spans, routeNodes, tracks));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No directional routes found for timetable markers");
        return result;
    }

    private static boolean contains(RouteScope route, String position) {
        String[] parts = position.trim().split("\\s+");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid railway position: " + position);
        if (parts.length >= 3 && !"U".equals(parts[2]) && !route.tracks().isEmpty()
                && !route.tracks().contains(parts[2]) && !route.tracks().contains("U")) return false;
        double[] span = route.spans().get(parts[0]);
        if (span == null) return false;
        String[] km = parts[1].split("\\+");
        double metres = Double.parseDouble(km[0]) * 1000 + (km.length == 2 ? Double.parseDouble(km[1]) : 0);
        return metres >= span[0] && metres <= span[1];
    }

    private static void emit(BufferedWriter writer, Set<String> emitted, String name, double position, String kind, String axis) throws IOException {
        String row = csv(name) + "," + position + "," + kind + (axis.isEmpty() ? "" : "," + axis);
        if (emitted.add(row)) { writer.write(row); writer.newLine(); }
    }

    static List<String> csvFields(
            String line, int lineNumber
    ) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException(
                    "Unclosed quoted field at CSV line " + lineNumber
            );
        }
        fields.add(field.toString());
        return fields;
    }

}
