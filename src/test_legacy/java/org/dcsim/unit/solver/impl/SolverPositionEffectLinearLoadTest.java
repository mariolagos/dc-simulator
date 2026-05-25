package org.dcsim.unit.solver.impl;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.build.StraightTrackBuilder;
import org.dcsim.solver.build.StraightTrackBuilder.SubCfg;
import org.dcsim.solver.build.StraightTrackBuilder.TrainCfg;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SolverPositionEffectLinearLoadTest {

    @Test
    public void voltage_drops_with_distance_for_linear_shunt_load() {

        final int SUB = 0;
        final int TRAIN = 1;
        final int GND = 2;

        final int groundIndex = GND;
        final int nNodes = 3;

        // source
        final double emf_V = 750.0;
        final double rint_ohm = 0.05;

        // line (this is the “distance effect” knob)
        final double r_per_km = 0.02;
        final double near_km = 0.1;
        final double far_km = 5.0;

        // linear load at TRAIN node to ground
        final double rLoad_ohm = 2.0;

        List<SubCfg> subs = List.of(
                new SubCfg("S1", SUB, emf_V, rint_ohm, true)
        );

        // We don’t actually need trains here; keep empty to avoid nonlinearities.
        List<TrainCfg> trains = List.of();

        // Build a base net just to get consistent nodeIds/indexById + substations list
        // (StraightTrackBuilder will create lines we don't want; we will override them)
        DcNet baseNear = StraightTrackBuilder.build(groundIndex, nNodes, near_km, r_per_km, subs, trains);
        DcNet baseFar = StraightTrackBuilder.build(groundIndex, nNodes, far_km, r_per_km, subs, trains);

        // Now replace lines:
        // - keep ONLY sub->train line with R = r_per_km * length_km
        // - add shunt load train->ground as a LineData with R = rLoad_ohm
        DcNet near = rebuildWithLines(baseNear, List.of(
                line(SUB, TRAIN, r_per_km * near_km),
                line(TRAIN, GND, rLoad_ohm)
        ));

        DcNet far = rebuildWithLines(baseFar, List.of(
                line(SUB, TRAIN, r_per_km * far_km),
                line(TRAIN, GND, rLoad_ohm)
        ));

        // Warm start near emf
        RealVector V0near = new ArrayRealVector(near.n(), emf_V);
        RealVector V0far = new ArrayRealVector(far.n(), emf_V);

        RealVector Vnear = DcIterativeSolver.solveVoltages(near, V0near);
        RealVector Vfar = DcIterativeSolver.solveVoltages(far, V0far);

        double vNear = Vnear.getEntry(TRAIN);
        double vFar = Vfar.getEntry(TRAIN);

        assertTrue("vNear must be > 0", vNear > 0.0);
        assertTrue("vFar must be > 0", vFar > 0.0);

        assertTrue("Expected vFar < vNear, got near=" + vNear + " far=" + vFar,
                vFar < vNear);
    }

    private static DcNet rebuildWithLines(DcNet base, List<LineData> newLines) {
        return new DcNet(
                base.n(),
                base.groundIndex(),
                base.nodeIds(),
                base.indexById(),
                new ArrayList<>(newLines),
                base.substations(),
                base.trains()
        );
    }

    private static LineData line(int a, int b, double rOhm) {
        // ✅ Adjust THIS line if your LineData constructor differs
        return new LineData("id", a, b, rOhm);
    }
}
