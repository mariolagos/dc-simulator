package org.dcsim.unit.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.SubstationData;
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

public class SolverKcl3S1TTest {

    private static final String GROUND = "GROUND";

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void threeSubsOneTrain_KCL_residual_is_small() throws Exception {
        GridModel<Real> model = (GridModel<Real>) load3S1T();
        model.setDynamicLineDevices(buildDynLines(model));

        DcNet net = NetBuilder.makeNet(model);
        RealVector V = DcIterativeSolver.solve(net);

        DcSystem base = MatrixBuilder.build(net, V.toArray());
        RealMatrix G = base.G().copy();
        RealVector J = base.J().copy();

        for (SubstationData ss : net.substations()) {
            DcStamps.stampSubstation(
                    V, G, J,
                    ss.a(), ss.b(),
                    ss.emf_V(), ss.rint_ohm(),
                    ss.allowBackfeed(),
                    1e-6,
                    1e-12
            );
        }

        for (TrainData tr : net.trains()) {
            DcStamps.stampTrain(
                    V, G, J,
                    tr,
                    0.0,
                    true
            );
        }

        clampNode(G, J, net.groundIndex());

        double kclInf = G.operate(V).subtract(J).getLInfNorm();
        assertTrue("KCL residual too large: " + kclInf, kclInf < 1e-5);

        for (int i = 0; i < V.getDimension(); i++) {
            double vi = V.getEntry(i);
            assertTrue("Non-finite voltage at idx=" + i + ": " + vi, Double.isFinite(vi));
        }
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

    private static void clampNode(RealMatrix G, RealVector J, int idx) {
        int n = G.getRowDimension();
        for (int j = 0; j < n; j++) G.setEntry(idx, j, 0.0);
        G.setEntry(idx, idx, 1.0);
        J.setEntry(idx, 0.0);

        for (int i = 0; i < n; i++) {
            if (i == idx) continue;
            G.setEntry(i, idx, 0.0);
        }
    }
}