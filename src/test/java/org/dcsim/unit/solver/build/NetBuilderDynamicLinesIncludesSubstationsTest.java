package org.dcsim.unit.solver.build;

import org.dcsim.electric.DcLine;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NetBuilderDynamicLinesIncludesSubstationsTest {

    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void dynamic_lines_enabled_still_includes_substations_and_trains() {
        // --- Arrange ---
        final String GND = "0";
        final String SUB = "1";
        final String TRAIN = "2";
        final int gnd_internal_id = 0;
        final int sub_internal_id = 1;
        final int train_intenal_id = 2;

        GridModel<Real> model = new GridModel<>(GND);

        // Nodes: match the convention you've used in TrainVoltageLongtableLoggingTest
        // NOTE: If your Node ctor differs, adjust here to whatever you already use.
        model.addNode(new Node<>(sub_internal_id, Real.ZERO, "SUB"));
        model.addNode(new Node<>(train_intenal_id, Real.ZERO, "TRAIN"));
        model.addNode(new Node<>(gnd_internal_id, Real.ZERO, "GND"));

        // Add Substation as a Device (NetBuilder iterates devices for Substation/TrainLoad)
        model.addDevice(new Substation(
                "S1",
                SUB,   // from = bus
                GND,   // to   = ground
                GND,   // groundNodeId (per your ctor)
                Real.fromDouble(750.0),
                Real.fromDouble(0.05)
        ));

        // Add TrainLoad as a Device
        TrainLoad tr = new TrainLoad("Train1", TRAIN, GND);
        tr.setRequestedPower(Real.fromDouble(10_000.0));
        tr.setMaxCurrent(Real.fromDouble(1e9));
        tr.setCutoffVoltage(Real.fromDouble(0.0));
        tr.setMaxVoltage(Real.fromDouble(2000.0));
        model.addDevice(tr);

        // Enable dynamic lines: one line between SUB and TRAIN with R=2.0
        List<org.dcsim.electric.Device<Real>> dyn = new ArrayList<>();
        dyn.add(new DcLine(SUB, TRAIN, Real.fromDouble(2.0)));
        model.setDynamicLineDevices(dyn);

        // --- Act ---
        DcNet net = NetBuilder.makeNet(model);

        // --- Assert ---
        assertNotNull(net);

        // Critical guard: dynamic lines must NOT cause substations/trains to disappear
        assertEquals("Expected exactly 1 substation in net", 1, net.substations().size());
        assertEquals("Expected exactly 1 train in net", 1, net.trains().size());

        // Dynamic lines should be present
        assertEquals("Expected exactly 1 line in net", 1, net.lines().size());

        // Optional: verify IDs were preserved
        assertEquals("S1", net.substations().get(0).id());
        assertEquals("Train1", net.trains().get(0).id());

        // Optional: verify the dynamic line R was used
        assertEquals(2.0, net.lines().get(0).r_ohm(), 1e-12);
    }
}
