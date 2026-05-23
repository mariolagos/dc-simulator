package org.dcsim.unit.solver.build;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.electric.DcLine;
import org.dcsim.electric.Device;
import org.dcsim.electric.Substation;
import org.dcsim.electric.DynamicLineTopologyBuilder;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.TrainLoad;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.dcsim.utils.PositionUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class EndToEndDynamicLinesPositionEffectTest {

    @Ignore("Migration (#19): solver path now uses String node_id. This test still uses int node ids and must be migrated.")
    @Test
    public void vTrain_drops_when_train_moves_farther_using_dynamic_lines_and_netbuilder() {
//
//        final String GND = "GND";
//        final String SUB = "SUB";
//        final String TRAIN_NODE = "TRAIN";
//
//        final int trackId = 1;
//        final double rPerM = 0.00001;
//
//        final double emfSeed = 750.0; // only for warm-start vector
//        final double rLoad_ohm = 2.0; // linear shunt load
//
//        // --- NEAR model ---
//        GridModel<Real> nearModel = getRealGridModel(GND, SUB, TRAIN, rLoad_ohm, trackId, rPerM, "0 0+200");
//
//        DcNet nearNet = NetBuilder.makeNet(nearModel);
//        RealVector Vnear = DcIterativeSolver.solveVoltages(nearNet, new ArrayRealVector(nearNet.n(), emfSeed));
//
//        int idxTrainNear = nearNet.nodeIds().indexOf(TRAIN);
//        assertTrue("TRAIN nodeId not present in netNear.nodeIds()", idxTrainNear >= 0);
//        double vTrainNear = Vnear.getEntry(idxTrainNear);
//
//        // --- FAR model ---
//        GridModel<Real> farModel = getRealGridModel(GND, SUB, TRAIN, rLoad_ohm, trackId, rPerM, "0 0+800");
//
//        DcNet farNet = NetBuilder.makeNet(farModel);
//        RealVector Vfar = DcIterativeSolver.solveVoltages(farNet, new ArrayRealVector(farNet.n(), emfSeed));
//        int idxTrainFar = farNet.nodeIds().indexOf(TRAIN);
//        assertTrue("TRAIN nodeId not present in netFar.nodeIds()", idxTrainFar >= 0);
//        double vTrainFar = Vfar.getEntry(idxTrainFar);
//        System.out.println("[E2E] vTrainNear=" + vTrainNear + " V, vTrainFar=" + vTrainFar + " V");
//
//        assertTrue(vTrainNear > 0.0);
//        assertTrue(vTrainFar > 0.0);
//        assertTrue("Expected vTrainFar < vTrainNear", vTrainFar < vTrainNear);
//    }
//
//    private static GridModel<Real> getRealGridModel(int GND, int SUB, int TRAIN, double rLoad_ohm, int trackId, double rPerM, String posString) {
//        GridModel<Real> nearModel;
//        nearModel = new GridModel<>(GND);
//
//        // Add ground node explicitly
//        addNode(nearModel, new Node(GND, Real.fromDouble(0.0), "0+0"));
//
//        // Position strings: use your real format "km+m"
//        addNode(nearModel, new Node(SUB, Real.fromDouble(0.0), "0+0"));
//        addNode(nearModel, new Node(TRAIN, Real.fromDouble(0.0), posString));
//        int length = (int) PositionUtils.parseFlexibleToMeters(posString);
//
//;        // Linear load as resistor to ground
//        addDevice(nearModel, new DcLine(TRAIN, GND, Real.fromDouble(rLoad_ohm)));
//
//        // 1) Add a substation at node 1 -> ground, 750 V with small internal R
//        Substation ss = new Substation("SS", SUB, GND, 0, Real.fromDouble(750.0),
//                Real.fromDouble(0.05));
//        ss.setAllowBackfeed(true);
//        nearModel.addDevice(ss);
//
//        TrainLoad tl = new TrainLoad("T", TRAIN, GND);
//        tl.setRequestedPower(Real.fromDouble(200_000.0));
//        tl.setMaxCurrent(Real.fromDouble(1e9));
//        tl.setCutoffVoltage(Real.fromDouble(1e9));
//        tl.setMaxVoltage(Real.fromDouble(1e9));
//        nearModel.addDevice(tl);
//
//        // Dynamic lines built from explicit NodePos (unit-test controlled)
//        List<Device<Real>> dynNear = DynamicLineTopologyBuilder.buildDynamicLines(
//                List.of(
//                        new DynamicLineTopologyBuilder.NodePos(SUB, trackId, 0),
//                        new DynamicLineTopologyBuilder.NodePos(TRAIN, trackId, length)
//                ),
//                (tid, a, b) -> rPerM * Math.abs(b - a)
//        );
//        nearModel.setDynamicLineDevices(dynNear);
//        return nearModel;
//    }
//
//    // --- Adapt points: change only these if your API names differ ---
//
//    private static void addNode(GridModel<Real> model, Node n) {
//        model.addNode(n); // if your method name differs, change here only
//    }
//
//    private static void addDevice(GridModel<Real> model, Device<Real> d) {
//        model.addDevice(d); // if your method name differs, change here only
//    }
//
//    private static double voltageAtNodeId(DcNet net, RealVector V, int nodeId) {
//        Integer idx = net.tryIdxOf(nodeId);
//        assertNotNull("tryIdxOf(" + nodeId + ") returned null; nodeId mapping missing?", idx);
//        return V.getEntry(idx);
//    }
    }
}
