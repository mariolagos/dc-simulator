package org.supply.model;

import org.supply.domain.Node;

public class InstallationFactory {

    public void build(Model model, Grid grid, Node root) {

        for (GridInstallation gi : grid.getInstallations()) {

            Node node = model.getNode(gi.getNodeId());

            Installation inst = new Installation(node);
            inst.setType(gi.getType());

            if (node.equals(root)) {
                inst.setSlack(true); // root = slack node
            }

            model.addInstallation(inst);
        }
    }
}