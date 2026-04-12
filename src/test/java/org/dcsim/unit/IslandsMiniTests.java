package org.dcsim.unit;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.impl.DcIslands;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class IslandsMiniTests {

    private static DcNet makeNet2NodesWithSubstationOnly(double E, double Rint, boolean allowBackfeed) {
        // nodes: [0]=ground, [1]=bus
        final int n = 2;
        final int gnd = 0;
        List<String> nodeIds = Arrays.asList("0","1");
        Map<String,Integer> idx = new HashMap<>();
        idx.put("0",0); idx.put("1",1);
        List<LineData> lines = new ArrayList<>();            // no lines
        List<SubstationData> subs = new ArrayList<>();
        subs.add(new SubstationData("SS", 1, 0, E, Rint, allowBackfeed));
        List<TrainData> trains = new ArrayList<>();
        return new DcNet(n, gnd, nodeIds, idx, lines, subs, trains);
    }

    @Test
    public void substationForwardBiased_yieldsSingleIsland() {
        final double E = 900.0;
        final DcNet net = makeNet2NodesWithSubstationOnly(E, 0.5, false);

        // V[1]=800, V[0]=0 => dV=800 <= E => forward => active edge => one island
        RealVector V = new ArrayRealVector(new double[]{0.0, 800.0});
        DcIslands.Result res = DcIslands.findIslands(net, V, 1e-6);
        assertEquals("single island expected", 1, res.components);
    }

    @Test
    public void substationBlocked_givesTwoIslands() {
        final double E = 900.0;
        final DcNet net = makeNet2NodesWithSubstationOnly(E, 0.5, false);

        // V[1]=950, V[0]=0 => dV=950 > E => diode blocks => two islands
        RealVector V = new ArrayRealVector(new double[]{0.0, 950.0});
        DcIslands.Result res = DcIslands.findIslands(net, V, 1e-6);
        assertEquals("two islands expected", 2, res.components);
    }

    @Test
    public void backfeedAllowed_mergesEvenIfAboveE() {
        final double E = 900.0;
        final DcNet net = makeNet2NodesWithSubstationOnly(E, 0.5, true);

        // allowBackfeed=true => edge always active
        RealVector V = new ArrayRealVector(new double[]{0.0, 1100.0});
        DcIslands.Result res = DcIslands.findIslands(net, V, 1e-6);
        assertEquals("single island expected", 1, res.components);
    }
}
