package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.dcsim.math.Real;

public class GridModelLoader {

    public static GridModel load(Config config) {
        System.out.println("=== GridModelLoader: Received config ===");
        System.out.println(config.root().render(ConfigRenderOptions.concise()));

        // Corrected key access (removed accidental whitespace)
        int groundNodeId = config.getInt("groundNodeId");

        GridModel model = new GridModel(groundNodeId);

        // Load all nodes
        var nodeList = config.getConfigList("nodes");
        for (var nodeConf : nodeList) {
            int id = nodeConf.getInt("id");
            String position = nodeConf.getString("position");
            Real voltage = Real.fromDouble(nodeConf.getDouble("voltage"));
            Node node = new Node(id, voltage, position);
            model.addNode(node);
        }

        // Load substations (from groundNode -> stationNode)
        var substationList = config.getConfigList("substations");
        for (var sub : substationList) {
            String id = sub.getString("id");
            int nodeId = sub.getInt("nodeId");
            Real emf = Real.fromDouble(sub.getDouble("emf"));
            Real rInternal = Real.fromDouble(sub.getDouble("internalResistance"));
            Substation ss = new Substation(id, nodeId, groundNodeId, emf, rInternal);
            model.addDevice(ss);
        }

        // Load trains (from trainNode -> groundNode)
        var trainList = config.getConfigList("trains");
        for (var tr : trainList) {
            String id = tr.getString("id");
            int nodeId = tr.getInt("nodeId");
            TrainLoad train = new TrainLoad(id, nodeId, groundNodeId);

            if (tr.hasPath("cutoffVoltage")) {
                Real cutoff = Real.fromDouble(tr.getDouble("cutoffVoltage"));
                train.setCutoffVoltage(cutoff);
            }

            model.addDevice(train);
        }

        // Optional lines
        if (config.hasPath("lines")) {
            var lineList = config.getConfigList("lines");
            for (var line : lineList) {
                int from = line.getInt("from");
                int to = line.getInt("to");
                Real resistance = Real.fromDouble(line.getDouble("resistance"));
                Line l = new Line(from, to, resistance);
                model.addDevice(l);
            }
        }

        return model;
    }
}
