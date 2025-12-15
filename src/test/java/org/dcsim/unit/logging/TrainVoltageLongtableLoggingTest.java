package org.dcsim.unit.logging;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.electric.*;
import org.dcsim.export.LongTableWriter;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import static org.junit.Assert.*;

public class TrainVoltageLongtableLoggingTest {

    @Test
    public void solver_logs_train_voltage_V_V_when_longwriter_is_injected() throws Exception {

        final int GND = 0;
        final int SUB = 1;
        final int TRAIN_NODE = 2;

        final double emf_V = 750.0;
        final double rint = 0.05;

        final double rPerM = 0.01;
        final double rLoad = 2.0;
        final int trackId = 1;

        // Build model
        GridModel<Real> model = new GridModel<>(GND);
        model.addNode(new Node(SUB, Real.fromDouble(0), "0+0"));
        model.addNode(new Node(TRAIN_NODE, Real.fromDouble(0), "0+200"));
        model.addNode(new Node(GND, Real.fromDouble(0), "0+0"));   // <-- VIKTIGT

//        model.addDevice(new Substation("S1", SUB, SUB, GND,
//                Real.fromDouble(emf_V), Real.fromDouble(rint)));

        model.addDevice(new Substation(
                "S1",
                SUB,
                GND,
                GND,
                Real.fromDouble(emf_V),
                Real.fromDouble(rint)
        ));

        System.out.println("[TEST] devicesView=" + model.devicesView());
        for (Device<Real> d : model.devicesView()) {
            System.out.println("[TEST] device class=" + d.getClass().getName());
        }

        Device<Real> ssDev = model.devicesView().stream()
                .filter(x -> "S1".equals(x.getId()))
                .findFirst().orElse(null);

        System.out.println("[TEST] S1 in devicesView? " + (ssDev != null));
        if (ssDev != null) {
            System.out.println("[TEST] S1 is Substation? " + (ssDev instanceof org.dcsim.electric.Substation));
        }

        // Linear shunt load at train
        TrainLoad trainLoad = new TrainLoad("Train1", TRAIN_NODE, GND);
        trainLoad.setRequestedPower(Real.fromDouble(10_000.));
        trainLoad.setCutoffVoltage(Real.fromDouble(0.0));   // vmin
        trainLoad.setMaxVoltage(Real.fromDouble(2000.0));   // vmax
        trainLoad.setMaxCurrent(Real.fromDouble(1e9));      // iMax stort

        model.addDevice(trainLoad);


        // Dynamic line between SUB and TRAIN
        model.setDynamicLineDevices(
                DynamicLineTopologyBuilder.buildDynamicLines(
                        List.of(
                                new DynamicLineTopologyBuilder.NodePos(SUB, trackId, 0),
                                new DynamicLineTopologyBuilder.NodePos(TRAIN_NODE, trackId, 200)
                        ),
                        (tid, a, b) -> rPerM * Math.abs(b - a)
                )
        );

        System.out.println("[TEST] trainLoad.reqW=" + trainLoad.getRequestedPower().asDouble());

        DcNet net = NetBuilder.makeNet(model);
        assertFalse("No substations in net!", net.substations().isEmpty());

        System.out.println("[DUMP] nodeIds=" + net.nodeIds() + " groundIndex=" + net.groundIndex());
        System.out.println("[DUMP] lines=" + net.lines());
        System.out.println("[DUMP] subs=" + net.substations());
        System.out.println("[DUMP] trains=" + net.trains());

        // hårda guards
        assertNotNull("SUB missing", net.tryIdxOf(SUB));
        assertNotNull("TRAIN missing", net.tryIdxOf(TRAIN_NODE));
        assertNotNull("GND missing", net.tryIdxOf(GND));

        // IMPORTANT: verifiera att substationen faktiskt är kopplad mot SUB och inte mot GND
        // (exakt vilka getters som finns beror på din SubstationData-typ; men dumpen ovan räcker för nu)


        System.out.println("[TEST-NET] nodeIds=" + net.nodeIds());
        System.out.println("[TEST-NET] groundIndex=" + net.groundIndex());
        System.out.println("[TEST-NET] lines=" + net.lines().size());

        assertNotNull("SUB missing in net", net.tryIdxOf(SUB));
        assertNotNull("TRAIN missing in net", net.tryIdxOf(TRAIN_NODE));


        // Prepare temp output file
        File out = File.createTempFile("longtable_test_", ".csv");
        out.deleteOnExit();

        // Inject writer into SOLVER (this is the critical part)
        LongTableWriter lw = new LongTableWriter(
                out.getAbsolutePath(),
                true,
                "testProject",
                "testScenario",
                "baseHash"
        );
        DcIterativeSolver.setLongWriter(lw);

        // Solve (t is not used by solveVoltages directly, but logging uses signalRow(t,...))
        RealVector V = DcIterativeSolver.solveVoltages(net, new ArrayRealVector(net.n(), emf_V));

        // Flush/close writer to ensure file contents are written
        lw.flush();
        lw.close();

        // Sanity: train node voltage from solution should be > 0
        Integer idxTrain = net.tryIdxOf(TRAIN_NODE);
        assertNotNull("tryIdxOf(TRAIN_NODE) returned null", idxTrain);
        double vExpected = V.getEntry(idxTrain);
        assertTrue("Expected solver V(train) > 0, got " + vExpected, vExpected > 0.0);

        // Now parse output file: find Train,Train1,V_V
        Double vLogged = findTrainVV(out, "Train1");
        assertNotNull("No Train/Train1 V_V row found in longtable output", vLogged);

        assertTrue("Logged Train.V_V must be > 0, got " + vLogged, vLogged > 0.0);

        // Should match solution (within tolerance)
        assertEquals(vExpected, vLogged, 1e-9);
    }

    private static Double findTrainVV(File csv, String trainId) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(csv))) {
            String line;
            while ((line = br.readLine()) != null) {
                // longtable format: time,objType,objId,signal,value,unit,domain,...
                // We match minimal fields to avoid depending on exact column count.
                if (line.contains(",Train,") && line.contains("," + trainId + ",") && line.contains(",V_V,")) {
                    String[] parts = line.split(",", -1);
                    // Find "value" column by locating "V_V" token and taking next field.
                    for (int i = 0; i < parts.length - 1; i++) {
                        if ("V_V".equals(parts[i])) {
                            String v = parts[i + 1];
                            if (v == null || v.isEmpty()) return null;
                            return Double.parseDouble(v);
                        }
                    }
                }
            }
        }
        return null;
    }
}
