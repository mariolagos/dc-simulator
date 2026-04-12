package org.dcsim.electric;

import org.dcsim.math.Real;
import org.dcsim.utils.PositionUtils;

public final class LegacyNodeFactory {

    public static Node fromPositionString(
            int internal_id,
            Real voltage,
            String position,
            String node_id) {

        int[] parsed = PositionUtils.parseFlexible(position);
        int section_id = parsed[0];
        int position_m = parsed[1];

        return new Node(internal_id, node_id, section_id, position_m, "");
    }
}