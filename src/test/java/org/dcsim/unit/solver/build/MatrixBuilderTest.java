package org.dcsim.unit.solver.build;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.DcSystem;
import org.dcsim.solver.build.MatrixBuilder;
import org.dcsim.solver.build.StraightTrackBuilder;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class MatrixBuilderTest {

    private static final double EPS = 1e-12;

    @Test
    public void stamps_single_resistor_between_two_nodes() {
        // Build net with 2 nodes and 1 line of R=10 ohm:
        // StraightTrackBuilder: segment_length_km = length_km/(nNodes-1)
        // Choose length_km=1, r_per_km=10 -> R=10
        DcNet net = StraightTrackBuilder.build(
                0,          // groundIndex
                2,          // nNodes
                1.0,        // length_km
                10.0,       // r_per_km
                Collections.emptyList(),
                Collections.emptyList()
        );

        DcSystem sys = MatrixBuilder.build(net, new double[net.n()]);

        RealMatrix G = G(sys);
        RealVector J = J(sys);

        // g = 1/R = 0.1
        double g = 0.1;

        assertEquals(2, G.getRowDimension());
        assertEquals(2, G.getColumnDimension());

        assertEquals(+g, G.getEntry(0,0), EPS);
        assertEquals(+g, G.getEntry(1,1), EPS);
        assertEquals(-g, G.getEntry(0,1), EPS);
        assertEquals(-g, G.getEntry(1,0), EPS);

        assertEquals(0.0, J.getEntry(0), EPS);
        assertEquals(0.0, J.getEntry(1), EPS);
    }

    @Test
    public void stamps_chain_of_three_nodes_as_tridiagonal() {
        // nNodes=3, length=1km, r_per_km=10 => segment_length=0.5km => R=5 => g=0.2
        DcNet net = StraightTrackBuilder.build(
                0,
                3,
                1.0,
                10.0,
                Collections.emptyList(),
                Collections.emptyList()
        );

        DcSystem sys = MatrixBuilder.build(net, new double[net.n()]);

        RealMatrix G = G(sys);
        RealVector J = J(sys);

        double g = 0.2;

        // Expected G for chain 0-1-2 with equal g:
        // [ g  -g   0 ]
        // [ -g 2g  -g ]
        // [ 0  -g   g ]
        assertEquals(+g,  G.getEntry(0,0), EPS);
        assertEquals(-g,  G.getEntry(0,1), EPS);
        assertEquals(0.0, G.getEntry(0,2), EPS);

        assertEquals(-g,  G.getEntry(1,0), EPS);
        assertEquals(2*g, G.getEntry(1,1), EPS);
        assertEquals(-g,  G.getEntry(1,2), EPS);

        assertEquals(0.0, G.getEntry(2,0), EPS);
        assertEquals(-g,  G.getEntry(2,1), EPS);
        assertEquals(+g,  G.getEntry(2,2), EPS);

        // J is zero (lines only) :contentReference[oaicite:1]{index=1}
        assertEquals(0.0, J.getEntry(0), EPS);
        assertEquals(0.0, J.getEntry(1), EPS);
        assertEquals(0.0, J.getEntry(2), EPS);
    }

    // ---- Accessors ----
    // If these don't compile: add getters in DcSystem (recommended) or rename here.
    private static RealMatrix G(DcSystem sys) {
        // try common getter names
        try { return (RealMatrix) sys.getClass().getMethod("G").invoke(sys); } catch (Exception ignored) {}
        try { return (RealMatrix) sys.getClass().getMethod("getG").invoke(sys); } catch (Exception ignored) {}
        try { return (RealMatrix) sys.getClass().getField("G").get(sys); } catch (Exception ignored) {}
        throw new AssertionError("Cannot access DcSystem.G. Add getG()/G() to DcSystem.");
    }

    private static RealVector J(DcSystem sys) {
        try { return (RealVector) sys.getClass().getMethod("J").invoke(sys); } catch (Exception ignored) {}
        try { return (RealVector) sys.getClass().getMethod("getJ").invoke(sys); } catch (Exception ignored) {}
        try { return (RealVector) sys.getClass().getField("J").get(sys); } catch (Exception ignored) {}
        throw new AssertionError("Cannot access DcSystem.J. Add getJ()/J() to DcSystem.");
    }
}
