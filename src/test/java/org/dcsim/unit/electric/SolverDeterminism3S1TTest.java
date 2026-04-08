package org.dcsim.unit.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SolverDeterminism3S1TTest {

    private static final String GROUND = "GROUND";

    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void threeSubsOneTrain_two_solves_produce_identical_voltages_within_tolerance() throws Exception {
        GridModel<Real> model = (GridModel<Real>) load3S1T();
        model.setDynamicLineDevices(buildDynLines(model));

        injectTrainLoad(model, "99", 200_000.0);

        DcNet net = NetBuilder.makeNet(model);

        RealVector V1 = DcIterativeSolver.solve(net);
        RealVector V2 = DcIterativeSolver.solve(net);

        assertEquals("Voltage vector dimension mismatch", V1.getDimension(), V2.getDimension());

        double maxAbsDiff = 0.0;
        for (int i = 0; i < V1.getDimension(); i++) {
            double d = Math.abs(V1.getEntry(i) - V2.getEntry(i));
            if (d > maxAbsDiff) maxAbsDiff = d;

            assertTrue("Non-finite V1 at idx=" + i, Double.isFinite(V1.getEntry(i)));
            assertTrue("Non-finite V2 at idx=" + i, Double.isFinite(V2.getEntry(i)));
        }

        assertTrue("Non-deterministic solve detected, max |V1-V2| = " + maxAbsDiff,
                maxAbsDiff < 1e-9);
    }

    private static void injectTrainLoad(GridModel<?> model, String trainNodeId, double reqW) {
        String gnd = model.getGroundNodeId();

        TrainLoad tl = new TrainLoad("TrainLoadSynthetic", trainNodeId, gnd);
        tl.setRequestedPower(Real.fromDouble(reqW));
        tl.setMaxCurrent(Real.fromDouble(1e30));
        tl.setCutoffVoltage(Real.fromDouble(1e30));
        tl.setMaxVoltage(Real.fromDouble(1e30));

        model.addDevice(tl);
    }

    private static GridModel<?> load3S1T() throws Exception {
        File f = new File("project/3subs1train/scenario1/application.conf");
        assertTrue("Missing scenario file: " + f.getAbsolutePath(), f.exists());

        Config cfg = ConfigFactory.parseFileAnySyntax(f, ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve();

        GridModelLoader loader = new GridModelLoader();
        return loader.load(cfg);
    }

    private static List<Device<Real>> buildDynLines(GridModel<?> model) {
        List<DynamicLineTopologyBuilder.NodePos> nodePos = new ArrayList<>();
        for (Node<?> n : model.getNodes()) {
            if (GROUND.equals(n.getNode_id())) continue;
            nodePos.add(new DynamicLineTopologyBuilder.NodePos(
                    n.getNode_id(), n.getTrackId(), n.getPositionM()));
        }

        return DynamicLineTopologyBuilder.buildDynamicLines(
                nodePos,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        );
    }
}