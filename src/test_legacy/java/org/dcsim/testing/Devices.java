package org.dcsim.testing;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.math.Real;

public final class Devices {

    private Devices() {
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String requireNodeId(GridModel<?> gm, int internalId) {
        for (Object nodeObj : gm.getNodes()) {
            Node<?> n = (Node<?>) nodeObj;

            if (n.get_internal_id() == internalId) {
                String nodeId = n.getNode_id();
                if (nodeId == null || nodeId.isBlank()) {
                    throw new IllegalArgumentException(
                            "Node has no node_id for internal id: " + internalId
                    );
                }
                return nodeId;
            }
        }

        throw new IllegalArgumentException("No node found for internal id: " + internalId);
    }
    // -------------------------------------------------------------------------
    // Substations - new API (String node_id)
    // -------------------------------------------------------------------------

    public static void addSubstation(
            GridModel<?> gm,
            String id,
            String nodeIdOnBus,
            double emfV,
            double rInternalOhm,
            boolean allowBackfeed,
            String description
    ) {
        String groundNodeId = gm.getGroundNodeId();
        if (groundNodeId == null || groundNodeId.isBlank()) {
            throw new IllegalArgumentException("GridModel groundNodeId is missing");
        }

        Substation ss = new Substation(
                id,
                nodeIdOnBus,
                groundNodeId,
                groundNodeId,
                Real.fromDouble(emfV),
                Real.fromDouble(rInternalOhm),
                description
        );
        ss.setAllowBackfeed(allowBackfeed);
        gm.addDevice(ss);
    }

    public static void addSubstation(
            GridModel<?> gm,
            String id,
            String nodeIdOnBus,
            double emfV,
            double rInternalOhm,
            boolean allowBackfeed
    ) {
        addSubstation(gm, id, nodeIdOnBus, emfV, rInternalOhm, allowBackfeed, null);
    }

    // -------------------------------------------------------------------------
    // Substations - legacy API (int internal_id) kept temporarily
    // -------------------------------------------------------------------------

    @Deprecated
    public static void addSubstation(
            GridModel<?> gm,
            String id,
            int nodeIdOnBus,
            double emfV,
            double rInternalOhm,
            boolean allowBackfeed,
            String description
    ) {
        addSubstation(
                gm,
                id,
                requireNodeId(gm, nodeIdOnBus),
                emfV,
                rInternalOhm,
                allowBackfeed,
                description
        );
    }

    @Deprecated
    public static void addSubstation(
            GridModel<?> gm,
            String id,
            int nodeIdOnBus,
            double emfV,
            double rInternalOhm,
            boolean allowBackfeed
    ) {
        addSubstation(gm, id, nodeIdOnBus, emfV, rInternalOhm, allowBackfeed, null);
    }

    // -------------------------------------------------------------------------
    // Lines - new API (String node_id)
    // -------------------------------------------------------------------------

    public static void addLine(
            GridModel<?> gm,
            String id,
            String fromNodeId,
            String toNodeId,
            double resistanceOhm,
            double lengthM
    ) {
        Line line = new Line(
                fromNodeId,
                toNodeId,
                Real.fromDouble(resistanceOhm),
                id,
                null,
                lengthM
        );
        gm.addDevice(line);
    }

    // -------------------------------------------------------------------------
    // Lines - legacy API (int internal_id) kept temporarily
    // -------------------------------------------------------------------------

    @Deprecated
    public static void addLine(
            GridModel<?> gm,
            String id,
            int fromNode,
            int toNode,
            double resistanceOhm,
            double lengthM
    ) {
        addLine(
                gm,
                id,
                requireNodeId(gm, fromNode),
                requireNodeId(gm, toNode),
                resistanceOhm,
                lengthM
        );
    }
}