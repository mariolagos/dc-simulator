package org.supply.loader;

import com.typesafe.config.Config;
import org.dcsim.math.Real;
import org.supply.domain.Line;
import org.supply.domain.Node;
import org.supply.model.GridModel;

import java.util.List;

public class LineFactory {

    public void build(GridModel model, Config gridConfig) {
        if (!gridConfig.hasPath("lines")) {
            return;
        }

        List<? extends Config> lines = gridConfig.getConfigList("lines");

        for (Config lineConfig : lines) {
            String nodeFromId = lineConfig.getString("node_from_id");
            String nodeToId = lineConfig.getString("node_to_id");
            double resistanceOhmPerM = lineConfig.getDouble("resistance_ohm_per_m");

            Node from = model.getNode(nodeFromId);
            Node to = model.getNode(nodeToId);

            Line line = new Line(
                    from,
                    to,
                    Real.fromDouble(resistanceOhmPerM)
            );

            model.addLine(line);
        }
    }
}