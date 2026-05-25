package org.dcsim.unit.solver.impl;

import org.dcsim.electric.DcLine;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.export.LongTableWriter;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static testUtils.NetHelpers.findTrainSignal;

public class TrainVoltageLoggedWhenTrainExistsTest {

    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void logs_train_voltage_row_when_net_contains_train() throws Exception {
        final String GROUND = "0";
        final String SUB = "1";
        final String TRAIN = "2";
        int ground_internal_id = 0;
        int sub_intenal_id = 1;
        int train_internal_id = 2;

        GridModel<Real> model = new GridModel<>(GROUND);

        // Nodes (adjust if your Node ctor differs)
        model.addNode(new Node<>(sub_intenal_id,   Real.ZERO, "SUB"));
        model.addNode(new Node<>(train_internal_id, Real.ZERO, "TRAIN"));
        model.addNode(new Node<>(ground_internal_id,   Real.ZERO, "GND"));

        // Substation: SUB -> GND
        model.addDevice(new Substation(
                "S1",
                SUB,
                GROUND,
                GROUND,
                Real.fromDouble(750.0),
                Real.fromDouble(0.05)
        ));

        // Train: TRAIN -> GND
        TrainLoad tr = new TrainLoad("Train1", TRAIN, GROUND);
        tr.setRequestedPower(Real.fromDouble(10_000.0));
        tr.setMaxCurrent(Real.fromDouble(1e9));
        tr.setCutoffVoltage(Real.fromDouble(0.0));
        tr.setMaxVoltage(Real.fromDouble(2000.0));
        model.addDevice(tr);

        // Dynamic line SUB <-> TRAIN
        List<org.dcsim.electric.Device<Real>> dyn = new ArrayList<>();
        dyn.add(new DcLine(SUB, TRAIN, Real.fromDouble(2.0)));
        model.setDynamicLineDevices(dyn);

        DcNet net = NetBuilder.makeNet(model);
        assertEquals("Precondition: expected 1 train in net", 1, net.trains().size());

        // Longtable to temp file
        Path tmp = Files.createTempFile("dcsim-longtable-", ".csv");
        LongTableWriter w = new LongTableWriter(
                tmp.toString(),
                true,
                "testProject",
                "testScenario",
                "baseHash"
        );

        DcIterativeSolver.setLongWriter(w);

        try {
            // Act
            DcIterativeSolver.setSimTimeSec(0);
            DcIterativeSolver.solveVoltages(net);
        } finally {
            // Always close to flush
            w.close();
            DcIterativeSolver.setLongWriter(null);
        }

        // Prepare temp output file
        File out = File.createTempFile("longtable_test_", ".csv");
        out.deleteOnExit();

        // Assert: must contain Train/Train1 V_V row
        assertTrue(
                "Expected Train/Train1 V_V row in longtable output.\nFILE:\n" + Files.readString(tmp),
                findTrainSignal(tmp.toFile(), "Train1", "V_node_V") != null);
    }
}
