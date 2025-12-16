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
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TrainVoltageLoggedWhenTrainExistsTest {

    @Test
    public void logs_train_voltage_row_when_net_contains_train() throws Exception {
        final int GND = 0;
        final int SUB = 1;
        final int TRAIN = 2;

        GridModel<Real> model = new GridModel<>(GND);

        // Nodes (adjust if your Node ctor differs)
        model.addNode(new Node<>(SUB,   Real.ZERO, "SUB"));
        model.addNode(new Node<>(TRAIN, Real.ZERO, "TRAIN"));
        model.addNode(new Node<>(GND,   Real.ZERO, "GND"));

        // Substation: SUB -> GND
        model.addDevice(new Substation(
                "S1",
                SUB,
                GND,
                GND,
                Real.fromDouble(750.0),
                Real.fromDouble(0.05)
        ));

        // Train: TRAIN -> GND
        TrainLoad tr = new TrainLoad("Train1", TRAIN, GND);
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
            DcIterativeSolver.solveVoltages(net);
        } finally {
            // Always close to flush
            w.close();
            DcIterativeSolver.setLongWriter(null);
        }

        String out = Files.readString(tmp);

        // Assert: must contain Train/Train1 V_V row
        assertTrue(
                "Expected Train/Train1 V_V row in longtable output.\nFILE:\n" + out,
                out.contains(",Train,Train1,V_V,")
        );
    }
}
