package org.supply.solver.model;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceSystem;
import org.supply.solver.electrical.AdmittanceSystemBuilder;
import org.supply.solver.electrical.CurrentInjection;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class FixedLoadElementTest {

    @Test
    public void stampsConstantPowerLoadUsingPreviousVoltage() {
        FixedLoadElement load =
                new FixedLoadElement(
                        "FL1",
                        "F1",
                        "R1",
                        1_000_000.0
                );

        CalculationNetwork network =
                new CalculationNetwork(
                        List.of(
                                node("F1"),
                                node("R1")
                        ),
                        List.of(),
                        List.of(),
                        List.of(load)
                );

        Map<String, Real> previousVoltages = Map.of(
                "F1", Real.fromDouble(750.0),
                "R1", Real.fromDouble(0.0)
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "R1",
                        List.of(
                                new CurrentInjection(
                                        "F1",
                                        Real.ZERO
                                ),
                                new CurrentInjection(
                                        "R1",
                                        Real.ZERO
                                )
                        ),
                        previousVoltages
                );

        Real[] j = system.currentVector();

        double expectedCurrentA =
                1_000_000.0 / 750.0;

        assertEquals(
                -expectedCurrentA,
                j[0].asDouble(),
                1e-9
        );

        assertEquals(
                expectedCurrentA,
                j[1].asDouble(),
                1e-9
        );
    }

    private static CalculationNode node(String id) {
        return new CalculationNode(
                id,
                null,
                "section-1",
                "track-1",
                0.0,
                CalculationNodeType.GRID_NODE
        );
    }
}