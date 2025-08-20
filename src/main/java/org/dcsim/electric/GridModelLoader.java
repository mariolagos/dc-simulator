package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.dcsim.math.Real;

public class GridModelLoader {

    public static GridModel load(Config config) {
        System.out.println("=== GridModelLoader: Received config ===");
        System.out.println(config.root().render(ConfigRenderOptions.concise()));

        // ---- Normalize level to the actual grid object ----
        Config g;
        if (config.hasPath("dcsim.grid")) {
            g = config.getConfig("dcsim.grid");
        } else if (config.hasPath("grid")) {
            g = config.getConfig("grid");
        } else {
            g = config; // assume already at grid level
        }

        // Ground node
        int groundNodeId = g.getInt("groundNodeId");
        GridModel model = new GridModel(groundNodeId);

        // Nodes
        var nodeList = g.getConfigList("nodes");
        for (var nodeConf : nodeList) {
            int id = nodeConf.getInt("id");
            String position = nodeConf.getString("position"); // supports "1 0+000" or "0+000"
            Real voltage = nodeConf.hasPath("voltage")
                    ? Real.fromDouble(nodeConf.getDouble("voltage"))
                    : Real.fromDouble(0.0);
            Node node = new Node(id, voltage, position);
            model.addNode(node);
        }

        // Substations
        var substationList = g.getConfigList("substations");
        for (var sub : substationList) {
            String id = sub.getString("id");
            Real emf = Real.fromDouble(sub.getDouble("emf"));
            Real rInternal = Real.fromDouble(sub.getDouble("internalResistance"));

            // Support both styles:
            // - old: nodeId (to ground)
            // - new: fromNode,toNode (explicit terminals)
            int nodeId;
            if (sub.hasPath("nodeId")) {
                nodeId = sub.getInt("nodeId");
                // Connected between groundNodeId and nodeId
                model.addDevice(new Substation(id, nodeId, groundNodeId, groundNodeId, emf, rInternal));
            } else {
                int fromNode = sub.getInt("fromNode");
                int toNode   = sub.getInt("toNode");
                // Expect fromNode == groundNodeId in new style (as in your log)
                if (fromNode != groundNodeId) {
                    System.err.println("[WARN] Substation '" + id + "' fromNode=" + fromNode
                            + " != groundNodeId=" + groundNodeId + " — using given terminals.");
                    // If your Substation supports a two-terminal ctor, prefer it; else map to legacy:
                    // model.addDevice(new Substation2T(id, fromNode, toNode, emf, rInternal));
                }
                nodeId = toNode; // map to legacy single-terminal API (other terminal is ground)
                model.addDevice(new Substation(id, nodeId, groundNodeId, groundNodeId, emf, rInternal));
            }
        }

        // Trains (optional at grid level; in v0.4 they come from traffic instead)
        if (g.hasPath("trains")) {
            var trainList = g.getConfigList("trains");
            for (var tr : trainList) {
                String id = tr.getString("id");

                if (tr.hasPath("fromNode") && tr.hasPath("toNode")) {
                    int from = tr.getInt("fromNode");
                    int to   = tr.getInt("toNode");
                    model.addDevice(new TrainLoad(id, from, to));
                } else if (tr.hasPath("nodeId")) {
                    int nodeId = tr.getInt("nodeId");
                    model.addDevice(new TrainLoad(id, nodeId, groundNodeId));
                } else {
                    throw new IllegalArgumentException("Train '" + id + "' must have (fromNode,toNode) or nodeId");
                }
            }
        }

        // Lines (optional)
        if (g.hasPath("lines")) {
            var lineList = g.getConfigList("lines");
            for (var line : lineList) {
                int from = line.getInt("from");
                int to   = line.getInt("to");
                Real resistance = Real.fromDouble(line.getDouble("resistance"));
                model.addDevice(new Line(from, to, resistance));
            }
        }

        return model;
    }
}
