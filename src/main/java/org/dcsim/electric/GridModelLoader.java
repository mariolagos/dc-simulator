package org.dcsim.electric;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.electric.GridModel;
import org.dcsim.math.Real;
import org.dcsim.utils.PositionUtils;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationType;
import org.dcsim.electric.Node;
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

            if (from.getNode_id().equals(to.getNode_id())) {
                throw new IllegalArgumentException("Substation " + id + " connects same node twice");
            }

            Substation ss = new Substation(
                    id,
                    from.getNode_id(),
                    to.getNode_id(),
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
        String groundNodeId = model.getGroundNodeId();

        for (var nodeConf : nodeList) {
            String nodeId = requireString(nodeConf, "node_id");
            String position = requireString(nodeConf, "position");

            Real voltage = nodeConf.hasPath("voltage")
                    ? Real.fromDouble(nodeConf.getDouble("voltage"))
                    : Real.ZERO;

            int trackId;
            int positionM;

            boolean isGround =
                    nodeId.equals(groundNodeId)
                            || "GND".equalsIgnoreCase(position);

            if (isGround) {
                trackId = -1;
                positionM = -1;
            } else {
                int[] secPos = PositionUtils.parseFlexible(position);
                trackId = secPos[0];
                positionM = secPos[1] * 1000;
            }

            int internalId = 0;

            Node node = null;
            Real voltage2 = Real.ZERO;
            for (var nodeConf2 : nodeList) {
                String nodeId2 = requireString(nodeConf2, "node_id");
                String position2 = requireString(nodeConf2, "position");

                voltage2 = nodeConf2.hasPath("voltage")
                        ? Real.fromDouble(nodeConf2.getDouble("voltage"))
                        : Real.ZERO;

                node = new Node(
                        internalId++,   // TEMP internal_id
                        voltage2,
                        position2
                );

                node.setName(nodeId2); // ← koppla node_id här

                boolean isGround2 =
                        nodeId2.equals(groundNodeId)
                                || "GND".equalsIgnoreCase(position2);

                if (isGround) {
                    node.setTrackId(-1);
                    node.setPositionM(-1);
                } else {
                    int[] secPos = PositionUtils.parseFlexible(position2);
                    node.setTrackId(secPos[0]);
                    node.setPositionM(secPos[1] * 1000);
                }

                model.addNode(node);
            }
            node.setVoltage(voltage2);

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
        Map<String, Node> nodesByNodeId = buildNodesByNodeId(model);

        for (var line : lineList) {
            String fromNodeId = requireString(line, "from_node_id");
            String toNodeId = requireString(line, "to_node_id");

            Node fromNode = requireNodeByNodeId(nodesByNodeId, fromNodeId);
            Node toNode = requireNodeByNodeId(nodesByNodeId, toNodeId);

            if (fromNode.getNode_id().equals(toNode.getNode_id())) {
                throw new IllegalArgumentException("Line connects same node twice: " + fromNodeId);
            }

            if (fromNode.getTrackId() != toNode.getTrackId()) {
                throw new IllegalArgumentException(
                        "Line crosses tracks: from node " + fromNodeId
                                + " track=" + fromNode.getTrackId()
                                + ", to node " + toNodeId
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
                        fromNodeId,
                        toNodeId,
                        resistancePerKm.times(lenM / 1000.0),
                        line.getString("description"),
                        line.getString("category"),
                        lenM
                );
            } else if (hasCat) {
                l = Line.ofCategory(
                        fromNodeId,
                        toNodeId,
                        resistancePerKm.times(lenM / 1000.0),
                        line.getString("category"),
                        lenM
                );
            } else if (hasDesc) {
                l = Line.of(
                        fromNodeId,
                        toNodeId,
                        resistancePerKm.times(lenM / 1000.0),
                        line.getString("description"),
                        lenM
                );
            } else {
                l = Line.of(
                        fromNodeId,
                        toNodeId,
                        resistancePerKm.times(lenM / 1000.0),
                        lenM
                );
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
            String groundNodeId
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

            String nodeIdOnBus = sub.hasPath("node_id")
                    ? sub.getString("node_id")
                    : sub.getString("nodeId"); // transitional compatibility

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
        Config root;
        Config grid;

        // Fall 1: full root with dcsim.grid
        if (config.hasPath("dcsim.grid")) {
            root = config.getConfig("dcsim");
            grid = root.getConfig("grid");

            // Fall 2: config is already the dcsim subtree
        } else if (config.hasPath("grid.nodes") || config.hasPath("grid.substations")) {
            root = config;
            grid = root.getConfig("grid");

            // Fall 3: config is already the grid block
        } else {
            root = ConfigFactory.empty();
            grid = config;
        }

        Config electrics = root.hasPath("electrics")
                ? root.getConfig("electrics")
                : ConfigFactory.empty();

        Config trainsCfg = root.hasPath("trains")
                ? root.getConfig("trains")
                : ConfigFactory.empty();

        // Train defaults (legacy pre-created trains only)
        double tCutoffDefault = trainsCfg.hasPath("defaults.cutoffVoltage")
                ? trainsCfg.getDouble("defaults.cutoffVoltage")
                : 850.0;

        double tMaxVDefault = trainsCfg.hasPath("defaults.maxVoltage")
                ? trainsCfg.getDouble("defaults.maxVoltage")
                : 1000.0;

        double tMaxADefault = trainsCfg.hasPath("defaults.maxCurrentA")
                ? trainsCfg.getDouble("defaults.maxCurrentA")
                : 4000.0;

        // Grid core
        String groundNodeId = requireString(grid, "groundNodeId");
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

        model.recomputeBackfeedFlag();

        // Legacy: pre-created trains defined under grid.trains
        if (grid.hasPath("trains")) {
            var trainList = grid.getConfigList("trains");

            for (var tr : trainList) {
                String id = tr.getString("id");

                String fromNodeId = tr.hasPath("from_node_id")
                        ? tr.getString("from_node_id")
                        : groundNodeId;

                String toNodeId = tr.hasPath("to_node_id")
                        ? tr.getString("to_node_id")
                        : groundNodeId;

                // Transitional compatibility
                if (tr.hasPath("node_id")) {
                    fromNodeId = tr.getString("node_id");
                    toNodeId = groundNodeId;
                }

                TrainLoad train = new TrainLoad(id, fromNodeId, toNodeId);

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
