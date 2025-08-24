// org/dcsim/electric/GridModelLoader.java
package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.dcsim.math.Real;

public class GridModelLoader {

    public static GridModel load(Config config) {
        System.out.println("=== GridModelLoader: Received config ===");
        System.out.println(config.root().render(ConfigRenderOptions.concise()));

        int groundNodeId = config.getInt("groundNodeId");
        GridModel model = new GridModel(groundNodeId);

        // nodes
        var nodeList = config.getConfigList("nodes");
        for (var nodeConf : nodeList) {
            int id = nodeConf.getInt("id");
            String position = nodeConf.getString("position");
            // voltage may be omitted in new configs → default 0.0
            Real voltage = nodeConf.hasPath("voltage")
                    ? Real.fromDouble(nodeConf.getDouble("voltage"))
                    : Real.ZERO;
            Node node = new Node(id, voltage, position);
            model.addNode(node);
        }

        // substations: connect FROM feeder node TO ground (diode toward network)
        var substationList = config.getConfigList("substations");
        for (var sub : substationList) {
            String id = sub.getString("id");
            int nodeId = sub.getInt("nodeId"); // explicit in our configs
            Real emf = Real.fromDouble(sub.getDouble("emf"));
            Real rInternal = Real.fromDouble(sub.getDouble("internalResistance"));

            // NOTE: orientation is important:
            //   from = network node (feeder), to = ground
            //   measureNode kept as nodeId (if your Substation uses it)
            Substation ss = new Substation(
                    id,
                    /* from */        nodeId,
                    /* to */          groundNodeId,
                    /* measureNode */ nodeId,
                    emf,
                    rInternal,
                    sub.hasPath("description") ? sub.getString("description") : null
            );
            model.addDevice(ss);
        }

        // lines (description/category optional)
        if (config.hasPath("lines")) {
            var lineList = config.getConfigList("lines");
            for (var line : lineList) {
                int from = line.getInt("from");
                int to   = line.getInt("to");
                Real resistance = Real.fromDouble(line.getDouble("resistance"));

                Line l;
                boolean hasDesc = line.hasPath("description");
                boolean hasCat  = line.hasPath("category");

                if (hasDesc && hasCat) {
                    l = new Line(from, to, resistance, line.getString("description"), line.getString("category"));
                } else if (hasCat) {
                    l = Line.ofCategory(from, to, resistance, line.getString("category"));
                } else if (hasDesc) {
                    l = Line.of(from, to, resistance, line.getString("description"));
                } else {
                    l = Line.of(from, to, resistance);
                }
                model.addDevice(l);
            }
        }

        // trains (legacy: trains inside grid; new flow spawns via 'traffic')
        if (config.hasPath("trains")) {
            var trainList = config.getConfigList("trains");
            for (var tr : trainList) {
                String id = tr.getString("id");
                // older configs may have fromNode/toNode; new use nodeId (to ground)
                int fromNode = tr.hasPath("fromNode") ? tr.getInt("fromNode") : groundNodeId;
                int toNode   = tr.hasPath("toNode")   ? tr.getInt("toNode")   : groundNodeId;
                if (tr.hasPath("nodeId")) {
                    // interpret as train between nodeId and ground
                    fromNode = tr.getInt("nodeId");
                    toNode   = groundNodeId;
                }
                TrainLoad train = new TrainLoad(id, fromNode, toNode);
                if (tr.hasPath("cutoffVoltage")) {
                    train.setCutoffVoltage(Real.fromDouble(tr.getDouble("cutoffVoltage")));
                }
                model.addDevice(train);
            }
        }

        System.out.println("Nodes: " + model.getNodes());
        System.out.println("Devices: " + model.getDevices());

        return model;
    }
}
