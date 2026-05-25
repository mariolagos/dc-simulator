package org.supply.domain;

import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.GridModel;

import org.dcsim.electric.GridResult;
import org.dcsim.electric.Substation;
import org.dcsim.math.Real;
import org.junit.Test;
import static org.junit.Assert.*;

// Temporarily disabled during node_id/GridModel migration.
// This test should be re-enabled once GridModel and device wiring are fully migrated to supply/node_id.


public class LineLossNoLoadTest {
/*
    @Test
    public void noTrainNoLoad_givesZeroLineLosses() {
        GridModel m = new GridModel("GROUND");
        */
/*
                    String node_id,
            int section_id,
            int position_m,
            String position

         *//*

        m.addNode(new Node("GROUND", 1, 0, "1 0+0"));
        m.addNode(new Node("1", 1, 0,"1 0+000"));
        m.addNode(new Node("2", 1, 1000, "1 1+000"));

        m.addDevice(new Substation("S1", 0, 1, 1, Real.fromDouble(1000), Real.fromDouble(0.05), null));
        m.addDevice(new Substation("S2", 0, 2, 2, Real.fromDouble(1000), Real.fromDouble(0.05), null));
        m.addDevice(Line.of(1, 2, Real.fromDouble(0.1), 1000));

        DcElectricSolver solver = new DcElectricSolver(); // din befintliga
        GridResult res = solver.solve(m, 0.0, 0);

        double Va = res.getLatestNodeVoltage("1").asDouble();
        double Vb = res.getLatestNodeVoltage("2").asDouble();
        double I  = (Va - Vb) / 0.1;

        assertEquals("No line current expected without load", 0.0, Math.abs(I), 1e-6);
    }
*/
}
