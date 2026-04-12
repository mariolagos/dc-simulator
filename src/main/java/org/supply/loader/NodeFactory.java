package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.Node;
import org.supply.model.GridModel;

import java.util.List;

public class NodeFactory {

    public void build(GridModel model, Config gridConfig) {

        List<? extends Config> nodes = gridConfig.getConfigList("nodes");

        for (Config nodeConfig : nodes) {

            String nodeId = nodeConfig.getString("node_id");
            String positionRwy = nodeConfig.getString("position_rwy");

            Node node = new Node(nodeId, positionRwy);

            model.addNode(node);
        }
    }
}