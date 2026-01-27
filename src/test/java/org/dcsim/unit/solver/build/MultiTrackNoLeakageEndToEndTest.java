package org.dcsim.unit.solver.build;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class MultiTrackNoLeakageEndToEndTest {

    @Test
    public void moving_train_on_track1_does_not_affect_track2_voltage() {

        final int GND = 0;

        // Track 1
        final int SUB1 = 10;
        final int TRAIN1 = 99;
        final int TRACK1 = 1;

        // Track 2
        final int SUB2 = 20;
        final int TRAIN2 = 199;
        final int TRACK2 = 2;

        final double rPerM = 0.01;
        final double rLoad = 2.0;
        final double emf_V = 750.0;
        final double rint = 0.05;

        // ---------- NEAR ----------
        GridModel<Real> near = new GridModel<>(GND);

        near.addNode(new Node(GND, Real.fromDouble(0), "0+0"));
        near.addNode(new Node(SUB1, Real.fromDouble(0), "0+0"));
        near.addNode(new Node(TRAIN1, Real.fromDouble(0), "0+200"));

        near.addNode(new Node(SUB2, Real.fromDouble(0), "0+0"));
        near.addNode(new Node(TRAIN2, Real.fromDouble(0), "0+200"));

        near.addDevice(new Substation("S1", SUB1, GND, GND,
                Real.fromDouble(emf_V), Real.fromDouble(rint)));
        near.addDevice(new Substation("S2", SUB2, GND, GND,
                Real.fromDouble(emf_V), Real.fromDouble(rint)));

        // Load only on TRAIN1
        near.addDevice(new DcLine(TRAIN1, GND, Real.fromDouble(rLoad)));

        near.setDynamicLineDevices(
                DynamicLineTopologyBuilder.buildDynamicLines(
                        List.of(
                                new DynamicLineTopologyBuilder.NodePos(SUB1, TRACK1, 0),
                                new DynamicLineTopologyBuilder.NodePos(TRAIN1, TRACK1, 200),

                                new DynamicLineTopologyBuilder.NodePos(SUB2, TRACK2, 0),
                                new DynamicLineTopologyBuilder.NodePos(TRAIN2, TRACK2, 200)
                        ),
                        (tid, a, b) -> rPerM * Math.abs(b - a)
                )
        );

        DcNet netNear = NetBuilder.makeNet(near);
        RealVector Vnear = DcIterativeSolver.solveVoltages(
                netNear, new ArrayRealVector(netNear.n(), emf_V)
        );

        double v1Near = Vnear.getEntry(netNear.tryIdxOf(TRAIN1));
        double v2Near = Vnear.getEntry(netNear.tryIdxOf(TRAIN2));

        // ---------- FAR ----------
        GridModel<Real> far = new GridModel<>(GND);

        far.addNode(new Node(GND, Real.fromDouble(0), "0+0"));
        far.addNode(new Node(SUB1, Real.fromDouble(0), "0+0"));
        far.addNode(new Node(TRAIN1, Real.fromDouble(0), "0+800"));

        far.addNode(new Node(SUB2, Real.fromDouble(0), "0+0"));
        far.addNode(new Node(TRAIN2, Real.fromDouble(0), "0+200"));

        far.addDevice(new Substation("S1", SUB1, GND, GND,
                Real.fromDouble(emf_V), Real.fromDouble(rint)));
        far.addDevice(new Substation("S2", SUB2, GND, GND,
                Real.fromDouble(emf_V), Real.fromDouble(rint)));

        far.addDevice(new DcLine(TRAIN1, GND, Real.fromDouble(rLoad)));

        far.setDynamicLineDevices(
                DynamicLineTopologyBuilder.buildDynamicLines(
                        List.of(
                                new DynamicLineTopologyBuilder.NodePos(SUB1, TRACK1, 0),
                                new DynamicLineTopologyBuilder.NodePos(TRAIN1, TRACK1, 800),

                                new DynamicLineTopologyBuilder.NodePos(SUB2, TRACK2, 0),
                                new DynamicLineTopologyBuilder.NodePos(TRAIN2, TRACK2, 200)
                        ),
                        (tid, a, b) -> rPerM * Math.abs(b - a)
                )
        );

        DcNet netFar = NetBuilder.makeNet(far);
        RealVector Vfar = DcIterativeSolver.solveVoltages(
                netFar, new ArrayRealVector(netFar.n(), emf_V)
        );

        double v1Far = Vfar.getEntry(netFar.tryIdxOf(TRAIN1));
        double v2Far = Vfar.getEntry(netFar.tryIdxOf(TRAIN2));

        System.out.println("[MT] TRAIN1 near=" + v1Near + " far=" + v1Far);
        System.out.println("[MT] TRAIN2 near=" + v2Near + " far=" + v2Far);

        // Track 1: position effect
        assertTrue("TRAIN1 voltage should drop when moving farther",
                v1Far < v1Near);

        // Track 2: unaffected
        assertEquals("TRAIN2 voltage must not change",
                v2Near, v2Far, 1e-9);
    }
}
