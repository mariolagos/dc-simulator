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
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SolverTrainPowerSanity3S1TTest {

    private static final String GROUND = "GROUND";

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void threeSubsOneTrain_train_power_sign_and_magnitude_are_sane() throws Exception {
        GridModel<Real> model = (GridModel<Real>) load3S1T();
        model.setDynamicLineDevices(buildDynLines(model));

        injectTrainLoad(model, "99", 200_000.0);

        DcNet net = NetBuilder.makeNet(model);
        assertEquals("Expected exactly one train after injection", 1, net.trains().size());

        RealVector V = DcIterativeSolver.solve(net);

        DcSystem base = MatrixBuilder.build(net, V.toArray());
        int n = base.G().getRowDimension();

        TrainData tr = net.trains().get(0);
        double req = tr.req_W();

        RealMatrix Gt = new Array2DRowRealMatrix(n, n);
        RealVector Jt = new ArrayRealVector(n);

        DcStamps.stampTrain(
                V, Gt, Jt,
                tr,
                0.0,
                true
        );

        RealVector Iin = Jt.subtract(Gt.operate(V));

        double P = 0.0;
        for (int i = 0; i < n; i++) {
            P += V.getEntry(i) * Iin.getEntry(i);
        }

        assertTrue("Non-finite train power", Double.isFinite(P));
        assertTrue("Non-finite req_W", Double.isFinite(req));
        assertTrue("req_W should be non-zero", Math.abs(req) > 1e-9);

        assertEquals("Train power sign mismatch. req_W=" + req + " P=" + P,
                Math.signum(req), Math.signum(P), 0.0);

        double absReq = Math.abs(req);
        double absP = Math.abs(P);

        assertTrue("Train power too small vs request. req_W=" + req + " P=" + P,
                absP > 0.01 * absReq);

        assertTrue("Train power unreasonably larger than request. req_W=" + req + " P=" + P,
                absP < 2.0 * absReq + 1.0);
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