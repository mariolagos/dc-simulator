package org.supply.solver.model;

import org.junit.Test;

import org.supply.math.Real;
import org.supply.solver.electrical.AdmittanceSystem;
import org.supply.solver.electrical.AdmittanceSystemBuilder;
import org.supply.solver.electrical.CurrentInjection;
import org.supply.solver.electrical.LinearSystemSolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class RegenerativeTrainElementTest {

    private static final double P_BRAKE_W = -1_200_000.0;
    private static final double U_START_V = 850.0;
    private static final double U_MAX_V = 900.0;

    @Test
    public void deliversFullRegenerationAtStartVoltage() {
        double currentA = RegenerativeTrainElement.regenerativeCurrentA(
                P_BRAKE_W,
                U_START_V,
                U_START_V,
                U_MAX_V
        );

        assertEquals(1_200_000.0 / 850.0, currentA, 0.1);
    }

    @Test
    public void deliversHalfCurrentHalfwayThroughVoltageRamp() {
        double fullCurrentA = 1_200_000.0 / 850.0;

        double currentA = RegenerativeTrainElement.regenerativeCurrentA(
                P_BRAKE_W,
                875.0,
                U_START_V,
                U_MAX_V
        );

        assertEquals(fullCurrentA / 2.0, currentA, 0.1);
    }

    @Test
    public void deliversNoRegenerationAtMaximumVoltage() {
        double currentA = RegenerativeTrainElement.regenerativeCurrentA(
                P_BRAKE_W,
                U_MAX_V,
                U_START_V,
                U_MAX_V
        );

        assertEquals(0.0, currentA, 0.1);
    }

    @Test
    public void stampsTheveninEquivalentInRegenerativeVoltageControlRegion() {
        RegenerativeTrainElement train =
                new RegenerativeTrainElement(
                        "T1",
                        "F1",
                        "R1",
                        P_BRAKE_W,
                        875.0,
                        U_START_V,
                        U_MAX_V
                );

        CalculationNetwork network =
                new CalculationNetwork(
                        List.of(
                                node("F1"),
                                node("R1")
                        ),
                        List.of(),
                        List.of(),
                        List.of(train)
                );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
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

        Real[][] g = system.conductanceMatrix();
        Real[] j = system.currentVector();

        double fullCurrentA =
                Math.abs(P_BRAKE_W) / U_START_V;

        double rRegenOhm =
                (U_MAX_V - U_START_V) / fullCurrentA;

        double expectedG =
                1.0 / rRegenOhm;

        double expectedJ =
                U_MAX_V / rRegenOhm;

        assertEquals(expectedG, g[0][0].asDouble(), 1e-6);
        assertEquals(-expectedG, g[0][1].asDouble(), 1e-6);
        assertEquals(-expectedG, g[1][0].asDouble(), 1e-6);
        assertEquals(expectedG, g[1][1].asDouble(), 1e-6);

        assertEquals(expectedJ, j[0].asDouble(), 1e-6);
        assertEquals(-expectedJ, j[1].asDouble(), 1e-6);
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

    @Test
    public void stampsRequestedRegenerativeCurrentBelowStartVoltage() {

        double voltageV = 800.0;

        RegenerativeTrainElement train =
                new RegenerativeTrainElement(
                        "T1",
                        "F1",
                        "R1",
                        P_BRAKE_W,
                        voltageV,
                        U_START_V,
                        U_MAX_V
                );

        CalculationNetwork network =
                new CalculationNetwork(
                        List.of(node("F1"), node("R1")),
                        List.of(),
                        List.of(),
                        List.of(train)
                );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
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

        Real[][] g = system.conductanceMatrix();
        Real[] j = system.currentVector();

        double powerW =
                Math.abs(P_BRAKE_W);

        double expectedConductanceS =
                powerW / (voltageV * voltageV);

        double expectedNortonCurrentA =
                2.0 * powerW / voltageV;

        assertEquals(
                expectedConductanceS,
                g[0][0].asDouble(),
                1e-9
        );

        assertEquals(
                -expectedConductanceS,
                g[0][1].asDouble(),
                1e-9
        );

        assertEquals(
                -expectedConductanceS,
                g[1][0].asDouble(),
                1e-9
        );

        assertEquals(
                expectedConductanceS,
                g[1][1].asDouble(),
                1e-9
        );

        assertEquals(
                expectedNortonCurrentA,
                j[0].asDouble(),
                1e-6
        );

        assertEquals(
                -expectedNortonCurrentA,
                j[1].asDouble(),
                1e-6
        );
    }}