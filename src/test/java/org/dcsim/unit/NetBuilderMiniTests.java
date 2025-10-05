package org.dcsim.unit;

import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.dcsim.solver.api.*;
import org.dcsim.solver.build.NetBuilder;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class NetBuilderMiniTests {

    @Test
    public void makeNet_basicTopology_ok() {
        // Modell: 0 (gnd), 1, 2; line 1-2, substation 1-0, train 2-0
        GridModel<Real> m = new GridModel<>(0);

        m.getNodes().add(new Node<>(0, Real.ZERO, "0 0+000"));
        m.getNodes().add(new Node<>(1, Real.ZERO, "0 0+001"));
        m.getNodes().add(new Node<>(2, Real.ZERO, "0 0+002"));

        Line l12 = new Line(1, 2, Real.fromDouble(0.5), "L_1_2", "u", 1000);
        m.getDevices().add(l12);

        Substation ss = new Substation("SS", 1, 0, 0, Real.fromDouble(900.0), Real.fromDouble(0.5));
        ss.setAllowBackfeed(false);
        m.getDevices().add(ss);

        TrainLoad tr = new TrainLoad("T", 2, 0);
        tr.setRequestedPower(Real.fromDouble(170000.0));
        tr.setMaxCurrent(Real.fromDouble(300.0));
        tr.setCutoffVoltage(Real.fromDouble(600.0));
        tr.setMaxVoltage(Real.fromDouble(1000.0));
        m.getDevices().add(tr);

        DcNet net = NetBuilder.makeNet(m);

        assertEquals("n", 3, net.n);
        assertEquals("ground idx", net.indexOfNodeId.get(0).intValue(), net.groundIndex);

        // Node mapping
        assertTrue(net.indexOfNodeId.containsKey(1));
        assertTrue(net.indexOfNodeId.containsKey(2));

        // One line
        assertEquals(1, net.lines.size());
        LineData L = net.lines.get(0);
        assertEquals("L_1_2", L.id());
        assertEquals(net.idxOf(1), L.a());
        assertEquals(net.idxOf(2), L.b(), 0);

        // Substation
        assertEquals(1, net.substations.size());
        SubstationData S = net.substations.get(0);
        assertEquals("SS", S.id());
        assertEquals(net.idxOf(1), S.a());
        assertEquals(net.idxOf(0), S.b());
        assertEquals(900.0, S.emf_V(), 1e-9);
        assertEquals(0.5, S.rint_ohm(), 1e-9);
        assertFalse(S.allowBackfeed());

        // Train
        assertEquals(1, net.trains.size());
        TrainData T = net.trains.get(0);
        assertEquals("T", T.id());
        assertEquals(net.idxOf(2), T.a());
        assertEquals(net.idxOf(0), T.b());
        assertEquals(170000.0, T.req_W(), 1e-6);
        assertEquals(300.0, T.imax_A(), 1e-6);
        assertEquals(600.0, T.cut_V(), 1e-6);
        assertEquals(1000.0, T.vmax_V(), 1e-6);
    }

    @Test
    public void makeNet_ignoresUnknownNodes_safely() {
        GridModel<Real> m = new GridModel<>(0);
        m.getNodes().add(new Node<>(0, Real.ZERO, "0 0+000"));
        m.getNodes().add(new Node<>(1, Real.ZERO, "0 0+001"));

        // Device that references a non-existing node (e.g., 99) should be skipped
        m.getDevices().add(new Line(1, 99, Real.fromDouble(1.0), "L_1_99", "u", 1000));

        DcNet net = NetBuilder.makeNet(m);

        assertEquals(2, net.n);
        assertEquals(0, net.lines.size()); // Skipped (b saknad)
    }
}
