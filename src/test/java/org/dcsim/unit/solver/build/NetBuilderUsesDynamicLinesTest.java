package org.dcsim.unit.solver.build;

import org.dcsim.electric.DcLine;
import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.build.NetBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NetBuilderUsesDynamicLinesTest {

    private static final double EPS = 1e-12;

    @Test
    @Ignore("Migration (#19): solver path now uses String node_id. This test still uses int node ids and must be migrated.")
    public void netbuilder_uses_dynamic_line_devices_when_present() {
//
//        final int GND = 0;
//        final int SUB = 10;
//        final int TRAIN = 99;
//
//        GridModel<Real> model = new GridModel<>(GND);
//
//        // Node(position) format: use km+m (e.g., "0+0", "0+200")
//        // Voltage seed irrelevant here; set 0.
//        model.addNode(new Node(GND, Real.fromDouble(0.0), "0+0"));
//        model.addNode(new Node(SUB, Real.fromDouble(0.0), "0+0"));
//        model.addNode(new Node(TRAIN, Real.fromDouble(0.0), "0+200"));
//
//        // Dynamic line (the thing we want NetBuilder to pick up)
//        double R = 2.5;
//        List<Device<Real>> dyn = List.of(
//                new DcLine(SUB, TRAIN, Real.fromDouble(R))
//        );
//
//        model.setDynamicLineDevices(dyn);
//
//        DcNet net = NetBuilder.makeNet(model);
//
//        // Assert: DcNet contains exactly our dynamic line (or at least contains it)
//        assertTrue("Expected at least 1 line in DcNet", net.lines().size() >= 1);
//
//        // Find a matching LineData between SUB and TRAIN
//        LineData match = null;
//        for (LineData l : net.lines()) {
//            int a = l.a();
//            int b = l.b();
//            boolean endpointsOk = (a == net.tryIdxOf(SUB) && b == net.tryIdxOf(TRAIN))
//                    || (a == net.tryIdxOf(TRAIN) && b == net.tryIdxOf(SUB));
//            if (endpointsOk) {
//                match = l;
//                break;
//            }
//        }
//
//        assertNotNull("Dynamic line SUB<->TRAIN not found in DcNet.lines()", match);
//
//        // Check resistance value
//        assertEquals(R, match.r_ohm(), EPS);
    }
}
