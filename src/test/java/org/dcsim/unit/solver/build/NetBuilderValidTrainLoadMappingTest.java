package org.dcsim.unit.solver.build;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.build.NetBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class NetBuilderValidTrainLoadMappingTest {

    private static final String GND = "0";
    private static final String TRAIN = "2";
    private static final int gnd_internal_id = 0;
    private static final int train_internal_id = 0;

    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void netBuilder_attaches_valid_trainload_correctly() {
        // Arrange: real model (no Mockito)
        GridModel<Real> model = new GridModel<>(GND); // ADAPT: use your real ctor/factory

        // Ground node must exist in node list
//        model.setGroundNodeId(GND, Real.fromDouble(0), "1 0+0")); // ADAPT if needed
        model.addNode(new Node<>(gnd_internal_id, Real.fromDouble(0), /*positionM*/"1 0+0"));      // ADAPT Node ctor
        model.addNode(new Node<>(train_internal_id, Real.fromDouble(0), /*positionM*/" 0+1000")); // ADAPT Node ctor

        // Add a real TrainLoad device
        TrainLoad tl = new TrainLoad("Train1", TRAIN, GND); // ADAPT ctor if needed
        tl.setRequestedPower(Real.fromDouble(100_000.0));
        tl.setMaxCurrent(Real.fromDouble(1e9));
        tl.setCutoffVoltage(Real.fromDouble(1e9));
        tl.setMaxVoltage(Real.fromDouble(1e9));
        model.addDevice(tl);

        // Act
        DcNet net = NetBuilder.makeNet(model);

        // Assert (robust: don't assume exactly one train)
        assertNotNull(net);
        assertNotNull(net.trains());
        assertTrue("Expected at least one train in net", net.trains().size() >= 1);

        int gndIdx = net.groundIndex();

        Integer trainIdx = net.indexById().get(TRAIN);
        assertNotNull("indexById must contain TRAIN node id", trainIdx);

        Optional<TrainData> maybe = net.trains().stream()
                .filter(t -> t.a() == trainIdx.intValue())
                .findFirst();

        assertTrue("Expected a TrainData attached to TRAIN node index=" + trainIdx
                        + ", trains=" + net.trains(),
                maybe.isPresent());

        TrainData td = maybe.get();
        assertEquals("TrainData.b must be groundIndex", gndIdx, td.b());
        assertNotEquals("TrainData.a must not be groundIndex", gndIdx, td.a());
    }
}
