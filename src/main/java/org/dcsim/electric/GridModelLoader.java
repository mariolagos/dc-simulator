// org/dcsim/electric/GridModelLoader.java
package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.math.Real;
import org.dcsim.utils.PositionUtils;

import java.io.IOException;

public class GridModelLoader {

    /**
     * Laddar ett {@link GridModel} från en Typesafe-config.
     *
     * Stöder flera "former" på den inkommande configen:
     *
     * 1) Hela roten med dcsim:
     *    {
     *      dcsim {
     *        grid { ... }
     *        electrics { ... }
     *        trains { ... }
     *      }
     *    }
     *
     * 2) Rot med alias:
     *    {
     *      dcsim { ... }
     *      grid = ${dcsim.grid}
     *    }
     *
     * 3) Redan nedskuren grid-subkonfig:
     *    {
     *      groundNodeId = 0
     *      nodes = [ ... ]
     *      substations = [ ... ]
     *      lines = [ ... ]
     *    }
     *
     * I fall (1) och (2) läses även electrics/trains-konfigurationer.
     * I fall (3) blir electrics/trains tomma (defaults används).
     */
    public static GridModel load(Config config) throws IOException {
        Config root;   // innehåller ev. dcsim, electrics, trains
        Config grid;   // själva grid-blocket (nodes/substations/lines)

        // --- Fall 1: full root med dcsim.grid ---
        if (config.hasPath("dcsim.grid")) {
            root = config.getConfig("dcsim");
            grid = root.getConfig("grid");

            // --- Fall 2: config ÄR redan dcsim-subträdet (har grid.* under sig) ---
        } else if (config.hasPath("grid.nodes") || config.hasPath("grid.substations")) {
            root = config;
            grid = root.getConfig("grid");

            // --- Fall 3: config ÄR själva grid-blocket (har nodes/substations direkt) ---
        } else {
            root = ConfigFactory.empty(); // inga electrics/trains tillgängliga
            grid = config;
        }

        // Sections (empty if missing → defaults nedan)
        Config electrics = root.hasPath("electrics") ? root.getConfig("electrics") : ConfigFactory.empty();
        Config trainsCfg = root.hasPath("trains") ? root.getConfig("trains") : ConfigFactory.empty();

        // ---- Electrics/Substations config (diode vs. backfeed + optional current limit) ----

        // ---- Default rectifier/backfeed behavior ----
        // Preferred: substations.defaults.rectifierType = BIDIR | DIODE
        // Legacy:    substations.defaults.allowBackfeed = true|false
        boolean ssDefaultAllowBackfeed;

        String ssDefaultRectifier = electrics.hasPath("substations.defaults.rectifierType")
                ? electrics.getString("substations.defaults.rectifierType")
                : null;

        if (ssDefaultRectifier != null) {
            ssDefaultAllowBackfeed = ssDefaultRectifier.equalsIgnoreCase("BIDIR");
        } else {
            ssDefaultAllowBackfeed =
                    electrics.hasPath("substations.defaults.allowBackfeed")
                            && electrics.getBoolean("substations.defaults.allowBackfeed");
        }
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
            int[] parsedPosition = PositionUtils.parseFlexible(position);
            Real voltage = nodeConf.hasPath("voltage")
                    ? Real.fromDouble(nodeConf.getDouble("voltage"))
                    : Real.ZERO;
            Node node = new Node(id, voltage, position);
            if ("GND".equals(position)) {
                node.setNodeKind(NodeKind.GROUND);
                node.setTrackId(-1);
                node.setPositionM(-1);
            } else {
                int[] secPos = PositionUtils.parseSectionAndMeters(position);
                node.setNodeKind(NodeKind.SUBSTATION); // TRAIN fixar vi senare via anchorNodeId
                node.setTrackId(secPos[0]);
                node.setPositionM(secPos[1]);
            }
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

            // allowBackfeed: default från electrics.*, per-station override i electrics.*,
            // och inline override på grid.substations-item (om närvarande).
            boolean allow = ssDefaultAllowBackfeed;

            // Preferred: rectifierType = BIDIR | DIODE
            String rectifier = null;
            if (ssOverrides.hasPath(id + ".rectifierType")) {
                rectifier = ssOverrides.getString(id + ".rectifierType");
            } else if (sub.hasPath("rectifierType")) {
                rectifier = sub.getString("rectifierType");
            }

            if (rectifier != null) {
                allow = rectifier.equalsIgnoreCase("BIDIR");
            } else {
                if (ssOverrides.hasPath(id + ".allowBackfeed")) {
                    allow = ssOverrides.getBoolean(id + ".allowBackfeed");
                    System.err.println("[DEPRECATION] electrics.substations.overrides." + id
                            + ".allowBackfeed is deprecated; use rectifierType=BIDIR|DIODE");
                }
                if (sub.hasPath("allowBackfeed")) {
                    allow = sub.getBoolean("allowBackfeed");
                    System.err.println("[DEPRECATION] grid.substations[].allowBackfeed is deprecated; use rectifierType=BIDIR|DIODE");
                }
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
        // Behåll nuvarande “force backfeed allowed” (om det är avsiktligt i din kodbas)
        model.recomputeBackfeedFlag();

        // Lines (description/category optional)
        if (grid.hasPath("lines")) {
            var lineList = grid.getConfigList("lines");
            for (var line : lineList) {
                int from = line.getInt("from");
                int to = line.getInt("to");
                Real resistancePerKm = Real.fromDouble(line.getDouble("rPerKm"));
                String positionFrom = model.nodeOrThrow(from).getPosition();
                String positionTo = model.nodeOrThrow(to).getPosition();
                double lenM = PositionUtils.distance(positionFrom, positionTo);

                Line l;
                boolean hasDesc = line.hasPath("description");
                boolean hasCat = line.hasPath("category");

                if (hasDesc && hasCat) {
                    l = new Line(from, to, resistancePerKm.times(lenM / 1000),
                            line.getString("description"),
                            line.getString("category"), lenM);
                } else if (hasCat) {
                    l = Line.ofCategory(from, to, resistancePerKm.times(lenM / 1000), line.getString("category"), lenM);
                } else if (hasDesc) {
                    l = Line.of(from, to, resistancePerKm.times(lenM / 1000), line.getString("description"), lenM);
                } else {
                    l = Line.of(from, to, resistancePerKm.times(lenM / 1000), lenM);
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
                int toNode = tr.hasPath("toNode") ? tr.getInt("toNode") : groundNodeId;
                if (tr.hasPath("nodeId")) {
                    fromNode = tr.getInt("nodeId");
                    toNode = groundNodeId;
                }

                TrainLoad train = new TrainLoad(id, fromNode, toNode);

                // Apply per-train overrides from trains.overrides.<ID>.*, else fall back to defaults,
                // else honor inline values on the device entry if present.
                double cutoff = tCutoffDefault;
                double maxV = tMaxVDefault;
                double maxA = tMaxADefault;

                if (trainsCfg.hasPath("overrides." + id)) {
                    var ov = trainsCfg.getConfig("overrides." + id);
                    if (ov.hasPath("cutoffVoltage")) cutoff = ov.getDouble("cutoffVoltage");
                    if (ov.hasPath("maxVoltage")) maxV = ov.getDouble("maxVoltage");
                    if (ov.hasPath("maxCurrentA")) maxA = ov.getDouble("maxCurrentA");
                } else {
                    if (tr.hasPath("cutoffVoltage")) cutoff = tr.getDouble("cutoffVoltage");
                    if (tr.hasPath("maxVoltage")) maxV = tr.getDouble("maxVoltage");
                    if (tr.hasPath("maxCurrentA")) maxA = tr.getDouble("maxCurrentA");
                }

                train.setCutoffVoltage(Real.fromDouble(cutoff));
                train.setMaxVoltage(Real.fromDouble(maxV));
                train.setMaxCurrent(Real.fromDouble(maxA));

                model.addDevice(train);
            }
        }

        ContractChecks.validateGridModel(model);

        return model;
    }


}
