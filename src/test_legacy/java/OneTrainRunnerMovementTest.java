// OneTrainRunnerMovementTest.java  (JUnit 4)
import org.dcsim.sim.EdgeProps;
import org.dcsim.sim.FixedNetworkStamper;
import org.dcsim.sim.LinearSolve;
import org.dcsim.sim.OneTrainRunner;
import org.dcsim.sim.TrainRuntime;
import org.junit.Test;
import testUtils.LinearSolver;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

public class OneTrainRunnerMovementTest {

    @Test
    public void oneTrainMovesAcrossTwoEdges_withoutNaNs_andMigrates() {
        // --- Noder ---
        // S: substation node, A: anchor node (hålls konstant), J: mellan-nod, K: ytter-nod, G: jord
        final int S = 0, A = 1, J = 2, K = 3, G = 4;
        final int nNodes = 5;

        // --- Kantparametrar (resistanser) ---
        final double rPerM = 0.01;   // Ohm/m
        final double L     = 100.0;  // m

        List<EdgeProps> path = Arrays.asList(
                new EdgeProps(S, J, rPerM, L),
                new EdgeProps(J, K, rPerM, L)
        );

        // --- Tågparametrar (start) ---
        final double vMS  = 20.0;     // m/s (≈72 km/h)
        final double pW   = 200_000;  // 200 kW (konstant P för enkelhet)
        TrainRuntime train = new TrainRuntime(A, vMS, pW);
        train.edgeIndex = 0;
        train.xM = 0.0;
        train.VaPrev = 1000.0;        // startgissning för Va (DC-nivå)

        // --- Substation som Thevenin->Norton ---
        // Vth=1000 V, Rs=10 Ω  =>  In=Vth/Rs=100 A, G=1/Rs=0.1 S
        FixedNetworkStamper stampFixed = (Y, Jvec) -> {
            double Vth = 1000.0;
            double Rs  = 10.0;
            double Gs  = 1.0 / Rs;
            double In  = Vth / Rs;
            Y.add(S, S, Gs);     // shunt (Thevenin Rs -> Norton G)
            Jvec.add(S, +In);    // injicerad strömkälla
        };

        // --- Lösare: återanvänd din testsnurra (LinearSolver) ---
        LinearSolve solver = (Ymat, rhs) -> LinearSolver.solve(Ymat, rhs);

        // --- Körparametrar / “parametrar att börja med" ---
        final double dt      = 0.05;   // s
        final double tEnd    = 12.0;   // s  ->  v*t = 240 m > 2*L (200 m), så vi hinner migrera
        final double Rmin    = 1e-6;   // Ω  -> skydd i ändpunkter
        final double epsFrac = 1e-3;   // 0.1% av L -> migreringströskel

        // --- Kör slingan ---
        OneTrainRunner.run(
                nNodes,
                path,
                train,
                G,              // jordnod
                stampFixed,
                solver,
                dt, tEnd,
                Rmin, epsFrac
        );

        // --- Snabb validering ---
        assertEquals("Train reached last edge", 1, train.edgeIndex);
        double eps = Math.max(1e-9, epsFrac * path.get(1).lengthM);
        assertTrue("Train near end of last edge", train.xM >= path.get(1).lengthM - eps - 1e-9);
        assertTrue("Train stopped (v=0 after end)", train.vMS == 0.0);
    }
}
