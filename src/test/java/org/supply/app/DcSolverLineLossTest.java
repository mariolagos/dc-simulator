package org.supply.app;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DcSolverLineLossTest {

    @Test
    public void aggregatesSplitBranchesByPhysicalLineAndExcludesInternalBranches() {
        CalculationNetwork network = new CalculationNetwork(
                List.of(),
                List.of(
                        branch("split-1", "LINE-1", "A", "B", 2.0),
                        branch("split-2", "LINE-1", "B", "C", 4.0),
                        branch("rectifier", "internal_SS1", "C", "D", 1.0)
                ),
                List.of(),
                List.of()
        );

        Map<String, Real> voltages = Map.of(
                "A", Real.fromDouble(100.0),
                "B", Real.fromDouble(90.0),
                "C", Real.fromDouble(70.0),
                "D", Real.fromDouble(0.0)
        );

        Map<String, Double> losses =
                DcSolver.lineLossesW(network, voltages);

        assertEquals(1, losses.size());
        assertEquals(150.0, losses.get("LINE-1"), 1e-9);
    }

    @Test
    public void accumulatesLineLossEnergyOverResultIntervals() {
        Map<String, Double> energy = new LinkedHashMap<>();

        DcSolver.accumulateLineLossEnergyJ(
                energy,
                Map.of("LINE-1", 150.0),
                2.0
        );
        DcSolver.accumulateLineLossEnergyJ(
                energy,
                Map.of("LINE-1", 50.0),
                1.0
        );

        assertEquals(350.0, energy.get("LINE-1"), 1e-9);
    }

    @Test
    public void trainPowerDeltaIsRequestedMinusDeliveredPower() {
        assertEquals(
                100_000.0,
                DcSolver.trainPowerDeltaW(700_000.0, 600_000.0),
                1e-9
        );
        assertEquals(
                -100_000.0,
                DcSolver.trainPowerDeltaW(-700_000.0, -600_000.0),
                1e-9
        );
    }

    @Test
    public void systemBalanceSubtractsLoadsAndLossesFromSubstationPower() {
        assertEquals(
                0.0,
                DcSolver.systemBalanceW(
                        1_000_000.0,
                        850_000.0,
                        100_000.0,
                        50_000.0
                ),
                1e-9
        );
    }

    @Test
    public void consumptionAndRegenerationAreAggregatedSeparately() {
        Map<String, Double> trainPowersW = Map.of(
                "motor-1", 700_000.0,
                "motor-2", 300_000.0,
                "brake-1", -600_000.0
        );

        assertEquals(
                1_000_000.0,
                DcSolver.consumedPowerW(trainPowersW),
                1e-9
        );
        assertEquals(
                600_000.0,
                DcSolver.regeneratedPowerW(trainPowersW),
                1e-9
        );
    }

    private static CalculationBranch branch(
            String id,
            String sourceId,
            String from,
            String to,
            double resistanceOhm
    ) {
        return new CalculationBranch(
                id,
                sourceId,
                from,
                to,
                Real.fromDouble(resistanceOhm)
        );
    }
}
