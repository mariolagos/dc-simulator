// org/dcsim/electric/GridModelLoader.java
package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.math.Real;
import org.dcsim.utils.PositionUtils;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationType;
import org.supply.domain.PowerConnection;
import org.supply.domain.PowerInstallation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GridModelLoader {
    // refactored start ------------------------------------------------------------------------------------------------
    private static void buildSubstationsFromInstallations(
            GridModel model,
            List<PowerInstallation> installations,
            List<PowerConnection> connections,
            Config electrics
    ) {
        Map<String, Node> nodesByNodeId = buildNodesByNodeId(model);

        boolean defaultAllowBackfeed =
                electrics.hasPath("substations.defaults.rectifierType")
                        ? electrics.getString("substations.defaults.rectifierType").equalsIgnoreCase("BIDIR")
                        : electrics.hasPath("substations.defaults.allowBackfeed")
                        && electrics.getBoolean("substations.defaults.allowBackfeed");

        Config overrides = electrics.hasPath("substations.overrides")
                ? electrics.getConfig("substations.overrides")
                : ConfigFactory.empty();

        for (PowerInstallation inst : installations) {

            if (inst.getInstallationType() != InstallationType.SUBSTATION) continue;

            String id = inst.getInstallationId();

            PowerConnection feeding = null;
            PowerConnection returning = null;

            for (PowerConnection c : connections) {
                if (!id.equals(c.getInstallationId())) continue;

                if (c.getConnectionType() == ConnectionType.FEEDING) feeding = c;
                if (c.getConnectionType() == ConnectionType.RETURN) returning = c;
            }

            if (feeding == null || returning == null) {
                throw new IllegalArgumentException("Substation " + id + " must have FEEDING and RETURN");
            }

            Node from = requireNode(nodesByNodeId, feeding.getNodeId());
            Node to = requireNode(nodesByNodeId, returning.getNodeId());

            if (from.getNode_id() == to.getNode_id()) {
                throw new IllegalArgumentException("Substation " + id + " connects same node twice");
            }

            Substation ss = new Substation(
                    id,
                    from.get_internal_id(),
                    to.get_internal_id(),
                    model.getGroundNodeId(),
                    inst.getEmfV(),
                    inst.getInternalResistanceOhm(),
                    inst.getDescription()
            );

            boolean allow = defaultAllowBackfeed;

            String rectifier = null;
            if (overrides.hasPath(id + ".rectifierType")) {
                rectifier = overrides.getString(id + ".rectifierType");
            } else if (inst.getRectifierType() != null) {
                rectifier = inst.getRectifierType();
            }

            if (rectifier != null) {
                allow = rectifier.equalsIgnoreCase("BIDIR");
            }

            ss.setAllowBackfeed(allow);

            if (overrides.hasPath(id + ".maxCurrentA")) {
                ss.setMaxCurrentA(overrides.getDouble(id + ".maxCurrentA"));
            }

            model.addDevice(ss);
        }

        model.recomputeBackfeedFlag();
    }

    private static Map<String, Node> buildNodesByNodeId(GridModel model) {
        Map<String, Node> map = new HashMap<>();

        for (Object nodeObject : model.getNodes()) {
            Node node = (Node) nodeObject;
            String nodeId = node.getNode_id();
            if (nodeId != null) {
                if (map.put(nodeId, node) != null) {
                    throw new IllegalArgumentException("Duplicate node_id: " + nodeId);
                }
            }
        }

        return map;
    }

    private static Node requireNode(Map<String, Node> map, String nodeId) {
        Node node = map.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node_id: " + nodeId);
        }
        return node;
    }

    private static void buildNodes(GridModel model, Config grid) {
        var nodeList = grid.getConfigList("nodes");
        int groundNodeId = model.getGroundNodeId();

        for (var nodeConf : nodeList) {
            int id = nodeConf.getInt("id");

            String position = nodeConf.getString("position");
            Real voltage = nodeConf.hasPath("voltage")
                    ? Real.fromDouble(nodeConf.getDouble("voltage"))
                    : Real.ZERO;

            Node node = new Node(id, voltage, position);

            if (nodeConf.hasPath("name")) {
                node.setName(nodeConf.getString("name"));
            }

            boolean isGround =
                    id == groundNodeId
                            || "GND".equalsIgnoreCase(position)
                            || (nodeConf.hasPath("name")
                            && "GND".equalsIgnoreCase(nodeConf.getString("name")));

            if (isGround) {
                node.setNodeKind(NodeKind.GROUND);
                node.setTrackId(-1);
                node.setPositionM(-1);
            } else {
                int[] secPos = PositionUtils.parseFlexible(position);
                node.setTrackId(secPos[0]);
                node.setPositionM(secPos[1] * 1000);
            }

            model.addNode(node);
        }
    }

    private static void buildLines(GridModel model, Config grid) {
        System.out.println("buildLines: hasPath(lines)=" + grid.hasPath("lines"));
        if (grid.hasPath("lines")) {
            System.out.println("buildLines: line count in config=" + grid.getConfigList("lines").size());
        }

        if (!grid.hasPath("lines")) return;

        var lineList = grid.getConfigList("lines");

        for (var line : lineList) {
            int from = line.getInt("from");
            int to = line.getInt("to");

            Node fromNode = model.nodeOrThrow(from);
            Node toNode = model.nodeOrThrow(to);

            if (fromNode.getTrackId() != toNode.getTrackId()) {
                throw new IllegalArgumentException(
                        "Line crosses tracks: from node " + from
                                + " track=" + fromNode.getTrackId()
                                + ", to node " + to
                                + " track=" + toNode.getTrackId());
            }

            Real resistancePerKm = Real.fromDouble(line.getDouble("rPerKm"));

            String positionFrom = fromNode.getPosition();
            String positionTo = toNode.getPosition();

            double lenM = PositionUtils.distance(positionFrom, positionTo);

            Line l;
            boolean hasDesc = line.hasPath("description");
            boolean hasCat = line.hasPath("category");

            if (hasDesc && hasCat) {
                l = new Line(
                        from,
                        to,
                        resistancePerKm.times(lenM / 1000),
                        line.getString("description"),
                        line.getString("category"),
                        lenM
                );
            } else if (hasCat) {
                l = Line.ofCategory(
                        from,
                        to,
                        resistancePerKm.times(lenM / 1000),
                        line.getString("category"),
                        lenM
                );
            } else if (hasDesc) {
                l = Line.of(
                        from,
                        to,
                        resistancePerKm.times(lenM / 1000),
                        line.getString("description"),
                        lenM
                );
            } else {
                l = Line.of(from, to, resistancePerKm.times(lenM / 1000), lenM);
            }

            System.out.println("ADDING LINE class=" + l.getClass().getName()
                    + " from=" + l.getFromNode()
                    + " to=" + l.getToNode()
                    + " len=" + l.getLength());

            model.addDevice(l);

            System.out.println("AFTER addDevice: model lines=" + model.getLines().size()
                    + " devices=" + model.getDevices().size());
        }
    }

    private static void buildSubstations(
            GridModel model,
            Config grid,
            Config electrics,
            int groundNodeId
    ) {
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

        var substationList = grid.getConfigList("substations");

        for (var sub : substationList) {
            String id = sub.getString("id");
            int nodeIdOnBus = sub.getInt("nodeId");

            Real emf = Real.fromDouble(sub.getDouble("emf"));
            Real rInternal = Real.fromDouble(sub.getDouble("internalResistance"));

            Substation ss = new Substation(
                    id,
                    nodeIdOnBus,
                    groundNodeId,
                    groundNodeId,
                    emf,
                    rInternal,
                    sub.hasPath("description") ? sub.getString("description") : null
            );

            boolean allow = ssDefaultAllowBackfeed;

            String rectifier = null;
            if (ssOverrides.hasPath(id + ".rectifierType")) {
                rectifier = ssOverrides.getString(id + ".rectifierType");
            } else if (sub.hasPath("rectifierType")) {
                rectifier = sub.getString("rectifierType");
            }

            if (rectifier != null) {
                allow = rectifier.equalsIgnoreCase("BIDIR");
            }

            ss.setAllowBackfeed(allow);

            if (ssOverrides.hasPath(id + ".maxCurrentA")) {
                ss.setMaxCurrentA(ssOverrides.getDouble(id + ".maxCurrentA"));
            }
            if (sub.hasPath("maxCurrentA")) {
                ss.setMaxCurrentA(sub.getDouble("maxCurrentA"));
            }

            model.addDevice(ss);
        }

        model.recomputeBackfeedFlag();
    }

    private static List<PowerInstallation> readPowerInstallations(Config grid) {
        if (!grid.hasPath("power_installations")) {
            return List.of();
        }

        List<? extends Config> installationList = grid.getConfigList("power_installations");
        List<PowerInstallation> installations = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (Config conf : installationList) {
            String installation_id = requireString(conf, "installation_id");

            if (!seenIds.add(installation_id)) {
                throw new IllegalArgumentException("Duplicate installation_id: " + installation_id);
            }

            InstallationType installation_type =
                    InstallationType.valueOf(requireString(conf, "installation_type").toUpperCase());

            // POINT may omit electrical parameters
            Real internal_resistance_ohm = conf.hasPath("internal_resistance_ohm")
                    ? Real.fromDouble(conf.getDouble("internal_resistance_ohm"))
                    : null;

            String rectifier_type = conf.hasPath("rectifier_type")
                    ? conf.getString("rectifier_type")
                    : null;

            Real emf_v = conf.hasPath("emf_v")
                    ? Real.fromDouble(conf.getDouble("emf_v"))
                    : null;

            if (installation_type == InstallationType.SUBSTATION) {
                if (internal_resistance_ohm == null) {
                    throw new IllegalArgumentException(
                            "Missing required field internal_resistance_ohm for SUBSTATION: " + installation_id);
                }
                if (rectifier_type == null || rectifier_type.isBlank()) {
                    throw new IllegalArgumentException(
                            "Missing required field rectifier_type for SUBSTATION: " + installation_id);
                }
            }

            String description = conf.hasPath("description")
                    ? conf.getString("description")
                    : null;

            installations.add(new PowerInstallation(
                    installation_id,
                    installation_type,
                    emf_v,
                    internal_resistance_ohm,
                    rectifier_type,
                    description
            ));
        }

        return installations;
    }

    private List<PowerConnection> readPowerConnections(Config grid) {
        if (!grid.hasPath("power_connections")) {
            return List.of();
        }

        List<? extends Config> connectionList = grid.getConfigList("power_connections");
        List<PowerConnection> connections = new ArrayList<>();

        for (Config conf : connectionList) {
            String installation_id = requireString(conf, "installation_id");
            String node_id = requireString(conf, "node_id");

            ConnectionType connection_type =
                    ConnectionType.valueOf(requireString(conf, "connection_type").toUpperCase());

            connections.add(new PowerConnection(
                    installation_id,
                    node_id,
                    connection_type
            ));
        }

        return connections;
    }

    private static String requireString(Config conf, String path) {
        if (!conf.hasPath(path)) {
            throw new IllegalArgumentException("Missing required field: " + path);
        }
        return conf.getString(path);
    }

    // refactored end --------------------------------------------------------------------------------------------------


    // migrations helper start

    private static Node requireNodeByNodeId(Map<String, Node> nodesByNodeId, String nodeId) {
        Node node = nodesByNodeId.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node_id: " + nodeId);
        }
        return node;
    }

    // migrations helpers end ------------------------------------------------------------------------------------------

    /**
     * Laddar ett {@link GridModel} från en Typesafe-config.
     * <p>
     * Stöder flera "former" på den inkommande configen:
     * <p>
     * 1) Hela roten med dcsim:
     * {
     * dcsim {
     * grid { ... }
     * electrics { ... }
     * trains { ... }
     * }
     * }
     * <p>
     * 2) Rot med alias:
     * {
     * dcsim { ... }
     * grid = ${dcsim.grid}
     * }
     * <p>
     * 3) Redan nedskuren grid-subkonfig:
     * {
     * groundNodeId = 0
     * nodes = [ ... ]
     * substations = [ ... ]
     * lines = [ ... ]
     * }
     * <p>
     * I fall (1) och (2) läses även electrics/trains-konfigurationer.
     * I fall (3) blir electrics/trains tomma (defaults används).
     */
    public GridModel load(Config config) throws IOException {
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

        buildNodes(model, grid);

        List<PowerInstallation> installations = readPowerInstallations(grid);
        List<PowerConnection> connections = readPowerConnections(grid);

        if (grid.hasPath("power_installations")) {
            buildSubstationsFromInstallations(model, installations, connections, electrics);
        } else {
            buildSubstations(model, grid, electrics, groundNodeId);
        }

        buildLines(model, grid);

        // Behåll nuvarande “force backfeed allowed” (om det är avsiktligt i din kodbas)
        model.recomputeBackfeedFlag();

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
