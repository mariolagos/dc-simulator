// org/dcsim/electric/GridModelLoader.java
package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.dcsim.math.Real;
import org.dcsim.utils.PositionUtils;

public class GridModelLoader {

    public static GridModel load(Config config) {
        // Accept either a root config or a subtree under "dcsim"
        Config root = config.hasPath("dcsim") ? config.getConfig("dcsim") : config;

        // Sections (empty if missing → defaults below)
        Config electrics = root.hasPath("electrics") ? root.getConfig("electrics") : ConfigFactory.empty();
        Config trainsCfg = root.hasPath("trains")    ? root.getConfig("trains")    : ConfigFactory.empty();
        Config grid      = root.getConfig("grid");   // must exist

        // ---- Electrics/Substations config (diode vs. backfeed + optional current limit) ----
        boolean ssDefaultAllowBackfeed =
                electrics.hasPath("substations.defaults.allowBackfeed")
                        && electrics.getBoolean("substations.defaults.allowBackfeed");
        Config ssOverrides =
                electrics.hasPath("substations.overrides")
                        ? electrics.getConfig("substations.overrides")
                        : ConfigFactory.empty();

        // ---- Train defaults (used only for legacy pre-created trains here) ----
        double tCutoffDefault = trainsCfg.hasPath("defaults.cutoffVoltage")
                ? trainsCfg.getDouble("defaults.cutoffVoltage") : 850.0;
        double tMaxVDefault = trainsCfg.hasPath("defaults.maxVoltage")
                ? trainsCfg.getDouble("defaults.maxVoltage") : 1000.0;
        double tMaxADefault = trainsCfg.hasPath("defaults.maxCurrentA")
                ? trainsCfg.getDouble("defaults.maxCurrentA") : 4000.0;

        // ---- Grid core ----
        int groundNodeId = grid.getInt("groundNodeId");
        GridModel model = new GridModel(groundNodeId);

        // Nodes (voltage optional → default 0.0)
        var nodeList = grid.getConfigList("nodes");
        for (var nodeConf : nodeList) {
            int id = nodeConf.getInt("id");
            String position = nodeConf.getString("position");
            Real voltage = nodeConf.hasPath("voltage")
                    ? Real.fromDouble(nodeConf.getDouble("voltage"))
                    : Real.ZERO;
            Node node = new Node(id, voltage, position);
            model.addNode(node);
        }

        // Substations: connect FROM feeder bus TO ground (diode toward the network)
        var substationList = grid.getConfigList("substations");
        for (var sub : substationList) {
            String id = sub.getString("id");
            int nodeIdOnBus = sub.getInt("nodeId"); // busbar node
            Real emf = Real.fromDouble(sub.getDouble("emf"));
            Real rInternal = Real.fromDouble(sub.getDouble("internalResistance"));

            Substation ss = new Substation(
                    id,
                    /* from */        nodeIdOnBus,
                    /* to   */        groundNodeId,
                    /* groundId */    groundNodeId,
                    emf,
                    rInternal,
                    sub.hasPath("description") ? sub.getString("description") : null
            );

            // allowBackfeed: default from electrics.*, per-station override in electrics.*,
            // and inline override on grid.substations item (if present).
            boolean allow = ssDefaultAllowBackfeed;
            if (ssOverrides.hasPath(id + ".allowBackfeed")) {
                allow = ssOverrides.getBoolean(id + ".allowBackfeed");
            }
            if (sub.hasPath("allowBackfeed")) {
                allow = sub.getBoolean("allowBackfeed");
            }
            ss.setAllowBackfeed(allow);

            // Optional per-station current limit (A)
            if (ssOverrides.hasPath(id + ".maxCurrentA")) {
                ss.setMaxCurrentA(ssOverrides.getDouble(id + ".maxCurrentA"));
            }
            if (sub.hasPath("maxCurrentA")) {
                ss.setMaxCurrentA(sub.getDouble("maxCurrentA"));
            }

            model.addDevice(ss);
        }
        model.recomputeBackfeedFlag();

        // Lines (description/category optional)
        if (grid.hasPath("lines")) {
            var lineList = grid.getConfigList("lines");
            for (var line : lineList) {
                int from = line.getInt("from");
                int to = line.getInt("to");
                Real resistancePerKm = Real.fromDouble(line.getDouble("resistance"));
                String positionFrom = model.getNodes().get(from).getPosition();
                String positionTo = model.getNodes().get(to).getPosition();
                double lenKm = PositionUtils.distance(positionFrom, positionTo)/1000;

                Line l;
                boolean hasDesc = line.hasPath("description");
                boolean hasCat  = line.hasPath("category");

                if (hasDesc && hasCat) {
                    l = new Line(from, to, resistancePerKm.times(lenKm),
                            line.getString("description"),
                            line.getString("category"));
                } else if (hasCat) {
                    l = Line.ofCategory(from, to, resistancePerKm.times(lenKm), line.getString("category"));
                } else if (hasDesc) {
                    l = Line.of(from, to, resistancePerKm.times(lenKm), line.getString("description"));
                } else {
                    l = Line.of(from, to, resistancePerKm.times(lenKm));
                }
                model.addDevice(l);
            }
        }

        // Legacy: pre-created trains defined under grid.trains (great for small deterministic tests).
        // This does NOT use TrainActor/traffic/power profiles.
        if (grid.hasPath("trains")) {
            var trainList = grid.getConfigList("trains");
            for (var tr : trainList) {
                String id = tr.getString("id");
                // Older configs may have fromNode/toNode; newer ones use nodeId→ground
                int fromNode = tr.hasPath("fromNode") ? tr.getInt("fromNode") : groundNodeId;
                int toNode   = tr.hasPath("toNode")   ? tr.getInt("toNode")   : groundNodeId;
                if (tr.hasPath("nodeId")) {
                    fromNode = tr.getInt("nodeId");
                    toNode   = groundNodeId;
                }

                TrainLoad train = new TrainLoad(id, fromNode, toNode);

                // Apply per-train overrides from trains.overrides.<ID>.*, else fall back to defaults,
                // else honor inline values on the device entry if present.
                double cutoff = tCutoffDefault;
                double maxV   = tMaxVDefault;
                double maxA   = tMaxADefault;

                if (trainsCfg.hasPath("overrides." + id)) {
                    var ov = trainsCfg.getConfig("overrides." + id);
                    if (ov.hasPath("cutoffVoltage")) cutoff = ov.getDouble("cutoffVoltage");
                    if (ov.hasPath("maxVoltage"))    maxV   = ov.getDouble("maxVoltage");
                    if (ov.hasPath("maxCurrentA"))   maxA   = ov.getDouble("maxCurrentA");
                } else {
                    if (tr.hasPath("cutoffVoltage")) cutoff = tr.getDouble("cutoffVoltage");
                    if (tr.hasPath("maxVoltage"))    maxV   = tr.getDouble("maxVoltage");
                    if (tr.hasPath("maxCurrentA"))   maxA   = tr.getDouble("maxCurrentA");
                }

                train.setCutoffVoltage(Real.fromDouble(cutoff));
                train.setMaxVoltage(Real.fromDouble(maxV));
                train.setMaxCurrent(Real.fromDouble(maxA));

                // NEW: constant requested power (kW) for simple tests (sign: + motoring, − braking).
                // Stored internally in watts.
                if (tr.hasPath("requestedPowerKW")) {
                    double kw = tr.getDouble("requestedPowerKW");
                    train.setRequestedPower(Real.fromDouble(kw * 1000.0));
                }

                model.addDevice(train);
            }
        }

        return model;
    }
}
