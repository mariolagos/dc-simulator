package org.dcsim.testing;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;

public final class ElectricBuilders {
    private ElectricBuilders() {}

    public static Node ensureNode(GridModel<?> gm, int id, double pos, String name) {
        Node n = new Node(id, Real.fromDouble(pos), name);
        n.setName(name);
        gm.addNode(n);

        String groundNodeId = gm.getGroundNodeId();
        if ((groundNodeId == null || !groundNodeId.equals("GND")) && id == 0) {
            gm.setGroundNodeId(n);
        }

        return n;
    }

    public static void ensureGround(GridModel<?> gm, int groundId) {
        for (Object nodeObj : gm.getNodes()) {
            Node n = (Node) nodeObj;
            if (n.get_internal_id() == groundId) {
                gm.setGroundNodeId(n);
                return;
            }
        }

        throw new IllegalArgumentException("No node found for internal id: " + groundId);
    }
}