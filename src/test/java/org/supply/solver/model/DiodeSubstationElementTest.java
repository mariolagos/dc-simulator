package org.supply.solver.model;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceSystem;
import org.supply.solver.electrical.AdmittanceSystemBuilder;
import org.supply.solver.electrical.CurrentInjection;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class DiodeSubstationElementTest {

    @Test
    public void enabledSubstationStampsNortonEquivalent() {
        DiodeSubstationElement substation =
                new DiodeSubstationElement(
                        "SS1",
                        "F1",
                        "R1",
                        Real.fromDouble(780.0),
                        Real.fromDouble(0.1),
                        true
                );

        AdmittanceSystem system =
                buildSystem(substation);

        Real[][] g =
                system.conductanceMatrix();

        Real[] j =
                system.currentVector();

        assertEquals(10.0, g[0][0].asDouble(), 1e-9);
        assertEquals(-10.0, g[0][1].asDouble(), 1e-9);
        assertEquals(-10.0, g[1][0].asDouble(), 1e-9);
        assertEquals(10.0, g[1][1].asDouble(), 1e-9);

        assertEquals(7800.0, j[0].asDouble(), 1e-9);
        assertEquals(-7800.0, j[1].asDouble(), 1e-9);
    }

    @Test
    public void disabledSubstationDoesNotStampElectricalContribution() {
        DiodeSubstationElement substation =
                new DiodeSubstationElement(
                        "SS1",
                        "F1",
                        "R1",
                        Real.fromDouble(780.0),
                        Real.fromDouble(0.1),
                        false
                );

        AdmittanceSystem system =
                buildSystem(substation);

        Real[][] g =
                system.conductanceMatrix();

        Real[] j =
                system.currentVector();

        assertEquals(0.0, g[0][0].asDouble(), 1e-9);
        assertEquals(0.0, g[0][1].asDouble(), 1e-9);
        assertEquals(0.0, g[1][0].asDouble(), 1e-9);
        assertEquals(0.0, g[1][1].asDouble(), 1e-9);

        assertEquals(0.0, j[0].asDouble(), 1e-9);
        assertEquals(0.0, j[1].asDouble(), 1e-9);
    }

    private static AdmittanceSystem buildSystem(
            DiodeSubstationElement substation
    ) {
        CalculationNetwork network =
                new CalculationNetwork(
                        List.of(
                                node("F1"),
                                node("R1")
                        ),
                        List.of(),
                        List.of(),
                        List.of(substation)
                );

        return new AdmittanceSystemBuilder().build(
                network,
                "R1",
                List.of(
                        new CurrentInjection(
                                "F1",
                                Real.fromDouble(0.0)
                        ),
                        new CurrentInjection(
                                "R1",
                                Real.fromDouble(0.0)
                        )
                )
        );
    }

    private static CalculationNode node(
            String id
    ) {
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