package org.dcsim.unit.solver.build;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.math.Real;
import org.dcsim.solver.build.NetBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

public class SubstationMustHaveDistinctTerminalsTest {

    @Test
    public void substation_with_same_from_and_to_node_is_rejected() {
        final int GND = 0;
        final int SUB = 1;

        GridModel<Real> model = new GridModel<>(GND);

        // Nodes
        model.addNode(new Node<>(SUB, Real.ZERO, "SUB"));
        model.addNode(new Node<>(GND, Real.ZERO, "GND"));

        // ❌ Invalid substation: from == to
        model.addDevice(new Substation(
                "S_BAD",
                SUB,   // from
                SUB,   // to   (invalid!)
                GND,
                Real.fromDouble(750.0),
                Real.fromDouble(0.05)
        ));

        try {
            NetBuilder.makeNet(model);
            fail("Expected NetBuilder to reject substation with identical terminals (from == to)");
        } catch (IllegalArgumentException e) {
            // good
            System.out.println("[OK] Caught expected exception: " + e.getMessage());
        }
    }
}
