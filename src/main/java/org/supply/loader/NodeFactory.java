package org.supply.model;

import com.typesafe.config.Config;
import org.supply.domain.Node;

import java.util.List;

public class NodeFactory {

    public void build(GridModel model, Config gridConfig) {

        List<? extends Config> nodes = gridConfig.getConfigList("nodes");

        for (Config nodeConfig : nodes) {

            int nodeId = nodeConfig.getInt("node_id");
            String positionRwy = nodeConfig.getString("position_rwy");
            int trackId = nodeConfig.getInt("track_id");

            Node node = new Node(nodeId, positionRwy, trackId);

            model.addNode(node);
        }
    }
}