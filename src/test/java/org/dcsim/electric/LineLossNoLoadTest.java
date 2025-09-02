package org.dcsim.electric;

import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.junit.Test;
import static org.junit.Assert.*;


public class LineLossNoLoadTest {

    @Test
    public void noTrainNoLoad_givesZeroLineLosses() {
        GridModel m = new GridModel(0);
        m.addNode(new Node(0, Real.ZERO, "GND"));
        m.addNode(new Node(1, Real.ZERO, "1 0+000"));
        m.addNode(new Node(2, Real.ZERO, "1 1+000"));

        m.addDevice(new Substation("S1", 0, 1, 1, Real.fromDouble(1000), Real.fromDouble(0.05), null));
        m.addDevice(new Substation("S2", 0, 2, 2, Real.fromDouble(1000), Real.fromDouble(0.05), null));
        m.addDevice(Line.of(1, 2, Real.fromDouble(0.1)));

        DcElectricSolver solver = new DcElectricSolver(); // din befintliga
        GridResult res = solver.solve(m, 0.0, 0);

        double Va = res.getLatestNodeVoltage(1).asDouble();
        double Vb = res.getLatestNodeVoltage(2).asDouble();
        double I  = (Va - Vb) / 0.1;

        assertEquals("No line current expected without load", 0.0, Math.abs(I), 1e-6);
    }
}
