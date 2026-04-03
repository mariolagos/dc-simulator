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

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void threeSubsOneTrain_KCL_residual_is_small() throws Exception {
        GridModel<Real> model = load3S1T();

        // Rebuild dynamic topology like production does (per tick)
        model.setDynamicLineDevices(buildDynLines(model));

        // Build compact-indexed net
        DcNet net = NetBuilder.makeNet(model);

        // Solve
        RealVector V = DcIterativeSolver.solve(net);

        // Rebuild G and J at the final V and compute residual ||G*V - J||_inf
        DcSystem base = MatrixBuilder.build(net, V.toArray());
        RealMatrix G = base.G().copy();
        RealVector J = base.J().copy();

        // Stamp substations (same idea as solver)
        for (SubstationData ss : net.substations()) {
            DcStamps.stampSubstation(
                    V, G, J,
                    ss.a(), ss.b(),
                    ss.emf_V(), ss.rint_ohm(),
                    ss.allowBackfeed(),
                    /*eps=*/1e-6,
                    /*gLeakDiag=*/1e-12
            );
        }

        // Stamp trains (assume motor enabled for 3S1T baseline)
        for (TrainData tr : net.trains()) {
            DcStamps.stampTrain(
                    V, G, J,
                    tr,
                    /*vminDefault=*/0.0,
                    /*motorEnabled=*/true
            );
        }

        // Clamp global ground (Dirichlet V=0). This mirrors the solver's "clampNode" idea.
        clampNode(G, J, net.groundIndex());

        double kclInf = G.operate(V).subtract(J).getLInfNorm();

        // Tolerance: keep it slightly looser than internal solver thresholds to avoid false failures.
        assertTrue("KCL residual too large: " + kclInf, kclInf < 1e-5);

        // Also assert no NaNs/Infs in voltages (cheap sanity)
        for (int i = 0; i < V.getDimension(); i++) {
            double vi = V.getEntry(i);
            assertTrue("Non-finite voltage at idx=" + i + ": " + vi, Double.isFinite(vi));
        }
    }

    // ---- helpers ----

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
            if (n.get_internal_id() == model.getGroundNodeId()) continue;
            nodePos.add(new DynamicLineTopologyBuilder.NodePos(n.get_internal_id(), n.getTrackId(), n.getPositionM()));
        }

        return DynamicLineTopologyBuilder.buildDynamicLines(
                nodePos,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        );
    }

    private static void clampNode(RealMatrix G, RealVector J, int idx) {
        int n = G.getRowDimension();

        // Zero row and set diag=1
        for (int j = 0; j < n; j++) G.setEntry(idx, j, 0.0);
        G.setEntry(idx, idx, 1.0);
        J.setEntry(idx, 0.0);

        // Zero column (except diag)
        for (int i = 0; i < n; i++) {
            if (i == idx) continue;
            G.setEntry(i, idx, 0.0);
        }
    }
}
