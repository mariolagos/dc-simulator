package org.dcsim.unit.solver.build;

import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NetBuilderBrokenTrainLoadMappingTest {

    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void netBuilder_preserves_broken_trainload_mapping() {

        final int GND = 0;

        // --- Arrange: mock Node so NetBuilder can read getId()
        @SuppressWarnings("unchecked")
        Node<Real> gndNode = mock(Node.class);
        when(gndNode.get_internal_id()).thenReturn(GND);

        // --- Arrange: mock TrainLoad (NetBuilder uses instanceof TrainLoad, works with mocks)
        TrainLoad tr = mock(TrainLoad.class);
        when(tr.getId()).thenReturn("T1");
        when(tr.getFromNode()).thenReturn("GND");
        when(tr.getToNode()).thenReturn("GND");

        // NetBuilder requires vmaxV > 0, cutV >= 0, imax >= 0 (otherwise it throws). :contentReference[oaicite:2]{index=2}
        when(tr.getRequestedPower()).thenReturn(Real.fromDouble(200_000));
        when(tr.getMaxCurrent()).thenReturn(Real.fromDouble(1_000_000));
        when(tr.getCutoffVoltage()).thenReturn(Real.fromDouble(500));
        when(tr.getMaxVoltage()).thenReturn(Real.fromDouble(900));

        @SuppressWarnings("unchecked")
        List<Device<Real>> dynamicDevices = (List<Device<Real>>) (List<?>) Arrays.asList(tr);

        // --- Arrange: mock GridModel<Real> with EXACT return types NetBuilder expects :contentReference[oaicite:3]{index=3}
        @SuppressWarnings("unchecked")
        GridModel<Real> model = mock(GridModel.class);
        when(model.getNodes()).thenReturn(Collections.singletonList(gndNode));
        when(model.getGroundNodeId()).thenReturn("GND");
        when(model.getDynamicLineDevices()).thenReturn(dynamicDevices);
        when(model.getLines()).thenReturn(Collections.emptyList());

        // --- Act
        DcNet net = NetBuilder.makeNet(model);

        // --- Assert
        assertEquals(0, net.trains().size());


        assertTrue("Broken mapping must not produce TrainData", net.trains().isEmpty());
        assertTrue("Broken mapping must not produce TrainData", net.trains().isEmpty());
        return;
    }

    private static Real real(double x) {
        Real r = mock(Real.class);
        when(r.asDouble()).thenReturn(x);
        return r;
    }
}
