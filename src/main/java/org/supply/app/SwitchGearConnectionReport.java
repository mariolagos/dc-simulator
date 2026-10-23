package org.supply.app;

import com.typesafe.config.Config;
import org.supply.domain.*;
import org.supply.loader.SwitchGearCompiler;
import org.supply.model.GridModel;
import org.supply.track.RwyCoordinateParser;
import java.util.*;

/** Input verification, generated from the same loaded wiring used by the network builder. */
public final class SwitchGearConnectionReport {
    private SwitchGearConnectionReport() { }

    public static Map<String, String> render(GridModel model, Config objectGrid) {
        objectGrid = org.supply.loader.SwitchGearTypeExpander.expand(objectGrid);
        Map<String, String> reports = new TreeMap<>();
        for (var gear : model.getSwitchGears()) {
            String id = gear.installationId();
            if (!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Unsafe report installation id: " + id);
            Config spec = stationSpec(objectGrid, id);
            var source = model.getInstallations().stream().filter(i -> i.getInstallationId().equals(id)).findFirst().orElseThrow();
            Map<String, SwitchGearTerminal> terminals = new HashMap<>();
            gear.terminals().forEach(t -> terminals.put(t.id(), t));
            var cables = gear.cables().stream().sorted(Comparator.comparing(CableConnection::id)).toList();
            StringBuilder csv = new StringBuilder("installation_id,cable_id,from_terminal,to_terminal,from_node_id,to_node_id,connection_type,from_position_rwy,to_position_rwy,length_m,conductor_type,resistance_ohm_per_km,multiplicity,resistance_ohm\n");
            for (var cable : cables) {
                var from = terminals.get(cable.fromTerminalId()); var to = terminals.get(cable.toTerminalId());
                row(csv, id, cable.id(), from.id(), to.id(), from.nodeId(), to.nodeId(), from.connectionType().name(),
                        model.getNode(from.nodeId()).getPositionRwy(), model.getNode(to.nodeId()).getPositionRwy(),
                        cable.lengthM(), cable.conductorType().id(), cable.conductorType().resistanceOhmPerKm(),
                        cable.multiplicity(), cable.resistanceOhm());
            }
            StringBuilder text = new StringBuilder("SWITCHGEAR CONNECTION CHECK\nInstallation: ").append(id)
                    .append("\nLayout: ").append(spec.getString("switchgear.model"))
                    .append("\nOrientation: ").append(spec.hasPath("orientation") ? spec.getString("orientation") : "NORMAL")
                    .append("\nRectifier: ").append(source.getRectifierType()).append("; enabled=").append(source.isEnabled())
                    .append("\nEMF_V: ").append(source.getEmfV().asDouble())
                    .append("\nInternal_resistance_ohm: ").append(source.getInternalResistanceOhm().asDouble())
                    .append("\n\nSOURCE CONNECTIONS\n");
            model.getInstallationConnections().stream().filter(c -> c.getInstallationId().equals(id))
                    .sorted(Comparator.comparing(c -> c.getConnectionType().name()))
                    .forEach(c -> text.append(c.getConnectionType()).append(" -> ").append(c.getNodeId()).append('\n'));
            text.append("\nEXTERNAL TERMINAL MAPPING\nRole | customer terminal | electrical node | railway position | external lines\n");
            var names = SwitchGearCompiler.terminalIds(spec.getConfig("switchgear").root().unwrapped());
            for (var entry : names.entrySet()) {
                var terminal = terminals.get(entry.getValue());
                List<String> lines = model.getLines().stream().filter(l -> l.getFromNode().getNodeId().equals(terminal.nodeId())
                        || l.getToNode().getNodeId().equals(terminal.nodeId())).map(Line::getLineId).sorted().toList();
                text.append(entry.getKey()).append(" | ").append(terminal.id()).append(" | ").append(terminal.nodeId())
                        .append(" | ").append(model.getNode(terminal.nodeId()).getPositionRwy()).append(" | ")
                        .append(lines.isEmpty() ? "(none)" : String.join(", ", lines)).append('\n');
            }
            text.append("\nFEEDING GAPS\n");
            for (String pair : List.of("NORTH", "SOUTH")) {
                var left = terminals.get(names.get(pair + "_POWER_LEFT"));
                var right = terminals.get(names.get(pair + "_POWER_RIGHT"));
                var coordinate = RwyCoordinateParser.parse(model.getNode(left.nodeId()).getPositionRwy());
                text.append("Track ").append(coordinate.getTrackId()).append(": ").append(left.id()).append(" [")
                        .append(left.nodeId()).append("] | GAP | ").append(right.id()).append(" [").append(right.nodeId())
                        .append("]; distinct_nodes=").append(!left.nodeId().equals(right.nodeId()))
                        .append("; same_railway_position=").append(model.getNode(left.nodeId()).getPositionRwy()
                                .equals(model.getNode(right.nodeId()).getPositionRwy())).append('\n');
            }
            text.append("Gap terminals are separate nodes. Electrical paths through the declared cables and busbars remain present.\n")
                    .append("Bus node railway positions are drawing/localization anchors, not cable lengths.\n")
                    .append("\nINTERNAL CABLES\n");
            for (var cable : cables) text.append(cable.id()).append(": ").append(cable.fromTerminalId()).append(" -> ")
                    .append(cable.toTerminalId()).append("; ").append(cable.lengthM()).append(" m; ")
                    .append(cable.conductorType().id()).append("; ").append(cable.conductorType().resistanceOhmPerKm())
                    .append(" ohm/km; parallel=").append(cable.multiplicity()).append("; R=")
                    .append(String.format(Locale.ROOT, "%.9g", cable.resistanceOhm())).append(" ohm\n");
            text.append("\nCable resistance = length_m / 1000 * resistance_ohm_per_km / multiplicity.\n")
                    .append("Cable CSV: ").append(id).append("_connections.csv\n")
                    .append("Scope: loaded input wiring and external line references; no timestep solved.\n");
            reports.put(id + "_connections.csv", csv.toString());
            reports.put(id + "_connections.txt", text.toString());
        }
        return Collections.unmodifiableMap(reports);
    }

    private static Config stationSpec(Config grid, String id) {
        Config objects = grid.getConfig("electricalObjects");
        for (String name : objects.root().keySet()) {
            Config spec = objects.getConfig(name);
            String objectId = spec.hasPath("id") ? spec.getString("id") : name;
            if (objectId.equals(id)) return spec.hasPath("type")
                    ? spec.withFallback(grid.getConfig("substationTypes").getConfig(spec.getString("type"))) : spec;
        }
        throw new IllegalArgumentException("No object description for switchgear " + id);
    }
    private static void row(StringBuilder out, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            String value = String.valueOf(values[i]);
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r"))
                out.append('"').append(value.replace("\"", "\"\"")).append('"');
            else out.append(value);
        }
        out.append('\n');
    }
}