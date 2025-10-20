package org.dcsim.testing;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;

public final class ElectricBuilders {
    private ElectricBuilders() {}

    public static Node ensureNode(GridModel<?> gm, int id, double pos, String name) {
        Node n = new Node(id, Real.fromDouble(pos), name);
        gm.addNode(n);
        if (gm.getGroundNodeId() != 0 && id == 0) {
            // om modellen har en annan ground: skriv över till 0
            gm.setGroundNodeId(n);
        }
        return n;
    }

    public static void ensureGround(GridModel<?> gm, int groundId) {
        gm.setGroundNodeId(gm.getNodeById(groundId));
    }
}
