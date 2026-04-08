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

public class SolverLosses3S1TTest {

    private static final String GROUND = "GROUND";

    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void threeSubsOneTrain_line_losses_are_non_negative_and_finite() throws Exception {
        GridModel<Real> model = (GridModel<Real>) load3S1T();
        model.setDynamicLineDevices(buildDynLines(model));

        DcNet net = NetBuilder.makeNet(model);
        RealVector V = DcIterativeSolver.solve(net);

        double totalLossW = 0.0;

        for (var line : net.lines()) {
            int a = line.a();
            int b = line.b();

            double Va = V.getEntry(a);
            double Vb = V.getEntry(b);
            double R = line.r_ohm();

            assertTrue("Line resistance must be > 0", R > 0.0);
            assertTrue("Non-finite Va", Double.isFinite(Va));
            assertTrue("Non-finite Vb", Double.isFinite(Vb));

            double I = (Va - Vb) / R;
            double Ploss = I * I * R;

            assertTrue("Non-finite line current", Double.isFinite(I));
            assertTrue("Non-finite line loss", Double.isFinite(Ploss));
            assertTrue("Negative line loss (should be impossible): " + Ploss, Ploss >= -1e-12);

            totalLossW += Ploss;
        }

        assertTrue("Total loss must be finite", Double.isFinite(totalLossW));
        assertTrue("Total loss must be >= 0", totalLossW >= -1e-9);
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