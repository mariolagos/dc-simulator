package org.dcsim.unit.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.build.DcSystem;
import org.dcsim.solver.build.MatrixBuilder;
import org.dcsim.solver.build.NetBuilder;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.dcsim.solver.impl.DcStamps;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SolverTrainPowerSanity3S1TTest {

    @Test
    public void threeSubsOneTrain_train_power_sign_and_magnitude_are_sane() throws Exception {
        GridModel<Real> model = load3S1T();
        model.setDynamicLineDevices(buildDynLines(model));

        // Inject a synthetic TrainLoad on the anchor node (99 -> ground).
        // This keeps v0.8 focused: we test solver + stamps without the traffic/profile pipeline.
        injectTrainLoad(model, /*trainNodeId=*/99, /*reqW=*/200_000.0);

        DcNet net = NetBuilder.makeNet(model);
        assertEquals("Expected exactly one train after injection", 1, net.trains().size());

        RealVector V = DcIterativeSolver.solve(net);

        // Dimension from base system (lines etc.)
        DcSystem base = MatrixBuilder.build(net, V.toArray());
        int n = base.G().getRowDimension();

        TrainData tr = net.trains().get(0);
        double req = tr.req_W();

        // Stamp train alone into zero matrices to isolate its contribution
        RealMatrix Gt = new Array2DRowRealMatrix(n, n);
        RealVector Jt = new ArrayRealVector(n);

        DcStamps.stampTrain(
                V, Gt, Jt,
                tr,
                /*vminDefault=*/0.0,
                /*motorEnabled=*/true
        );

        // Current flowing INTO the train at each node contribution:
        // I_into_train = (Gt*V - Jt)
        RealVector Iin = Jt.subtract(Gt.operate(V));

        // Power absorbed by the train from the network:
        // P = sum_i V_i * I_into_train_i
        double P = 0.0;
        for (int i = 0; i < n; i++) {
            P += V.getEntry(i) * Iin.getEntry(i);
        }

        assertTrue("Non-finite train power", Double.isFinite(P));
        assertTrue("Non-finite req_W", Double.isFinite(req));
        assertTrue("req_W should be non-zero", Math.abs(req) > 1e-9);

        // Sign sanity: +req => consumes power, -req => delivers power
        assertEquals("Train power sign mismatch. req_W=" + req + " P=" + P,
                Math.signum(req), Math.signum(P), 0.0);

        // Magnitude sanity (wide bounds on purpose)
        double absReq = Math.abs(req);
        double absP = Math.abs(P);

        assertTrue("Train power too small vs request. req_W=" + req + " P=" + P,
                absP > 0.01 * absReq); // > 1% of requested

        assertTrue("Train power unreasonably larger than request. req_W=" + req + " P=" + P,
                absP < 2.0 * absReq + 1.0); // generous upper bound
    }

    // ---- helpers ----

    private static void injectTrainLoad(GridModel<Real> model, int trainNodeId, double reqW) {
        int gnd = model.getGroundNodeId();

        TrainLoad tl = new TrainLoad("TrainLoadSynthetic", trainNodeId, gnd);
        tl.setRequestedPower(Real.fromDouble(reqW));

        // Make sure we don't accidentally clamp current/voltage in this sanity test
        tl.setMaxCurrent(Real.fromDouble(1e30));
        tl.setCutoffVoltage(Real.fromDouble(1e30)); // so regen logic won't zero current (not relevant here)
        tl.setMaxVoltage(Real.fromDouble(1e30));

        model.addDevice(tl);
    }

    private static GridModel<Real> load3S1T() throws Exception {
        File f = new File("project/3subs1train/scenario1/application.conf");
        assertTrue("Missing scenario file: " + f.getAbsolutePath(), f.exists());

        Config cfg = ConfigFactory.parseFileAnySyntax(f, ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve();

        @SuppressWarnings("unchecked")
        GridModel<Real> model = (GridModel<Real>) GridModelLoader.load(cfg);
        return model;
    }

    private static List<Device<Real>> buildDynLines(GridModel<Real> model) {
        List<DynamicLineTopologyBuilder.NodePos> nodePos = new ArrayList<>();
        for (Node<Real> n : model.getNodes()) {
            if (n.getId() == model.getGroundNodeId()) continue;
            nodePos.add(new DynamicLineTopologyBuilder.NodePos(n.getId(), n.getTrackId(), n.getPositionM()));
        }

        return DynamicLineTopologyBuilder.buildDynamicLines(
                nodePos,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        );
    }
}
