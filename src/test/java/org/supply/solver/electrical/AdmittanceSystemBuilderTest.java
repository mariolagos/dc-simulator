package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.List;

import static org.junit.Assert.*;

public final class AdmittanceSystemBuilderTest {

    @Test
    public void buildsNodeIndexOrderingFromCalculationNetworkNodeOrder() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0),
                        node("C", 200.0)
                ),
                List.of()
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "A");

        assertEquals(3, system.size());

        assertEquals(0, system.nodeIndex("A"));
        assertEquals(1, system.nodeIndex("B"));
        assertEquals(2, system.nodeIndex("C"));

        assertEquals("A", system.nodeIds().get(0));
        assertEquals("B", system.nodeIds().get(1));
        assertEquals("C", system.nodeIds().get(2));
    }

    @Test
    public void initializesZeroConductanceMatrixAndCurrentVector() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0)
                ),
                List.of()
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "A");

        assertEquals(2, system.conductanceMatrix().length);
        assertEquals(2, system.conductanceMatrix()[0].length);
        assertEquals(2, system.currentVector().length);

        assertEquals(0.0, system.conductanceMatrix()[0][0].asDouble(), 1e-9);
        assertEquals(0.0, system.conductanceMatrix()[0][1].asDouble(), 1e-9);
        assertEquals(0.0, system.conductanceMatrix()[1][0].asDouble(), 1e-9);
        assertEquals(0.0, system.conductanceMatrix()[1][1].asDouble(), 1e-9);

        assertEquals(0.0, system.currentVector()[0].asDouble(), 1e-9);
        assertEquals(0.0, system.currentVector()[1].asDouble(), 1e-9);
    }

    private static CalculationNode node(String id, double positionM) {
        return new CalculationNode(
                id,
                null,
                "section-1",
                "track-1",
                positionM,
                CalculationNodeType.GRID_NODE
        );
    }

    @Test
    public void stampsPassiveBranchConductanceIntoMatrix() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0)
                ),
                List.of(
                        new CalculationBranch(
                                "branch-1",
                                "source-1",
                                "A",
                                "B",
                                Real.fromDouble(2.0)
                        )
                )
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "A");

        Real[][] g = system.conductanceMatrix();

        assertEquals(0.5, g[0][0].asDouble(), 1e-9);
        assertEquals(-0.5, g[0][1].asDouble(), 1e-9);
        assertEquals(-0.5, g[1][0].asDouble(), 1e-9);
        assertEquals(0.5, g[1][1].asDouble(), 1e-9);
    }

    @Test
    public void exposesReferenceNodeIndex() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("feed", 0.0),
                        node("return", 100.0)
                ),
                List.of()
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "return");

        assertEquals("return", system.referenceNodeId());
        assertEquals(1, system.referenceNodeIndex());
    }

    @Test
    public void rejectsUnknownReferenceNode() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("feed", 0.0)
                ),
                List.of()
        );

        try {
            new AdmittanceSystemBuilder().build(network, "missing-return");
            fail("Expected IllegalArgumentException");

        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("missing-return"));
        }
    }

    @Test
    public void stampsCurrentInjectionsIntoCurrentVector() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0)
                ),
                List.of()
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "B",
                        List.of(
                                new CurrentInjection(
                                        "A",
                                        Real.fromDouble(10.0)
                                ),
                                new CurrentInjection(
                                        "B",
                                        Real.fromDouble(-10.0)
                                )
                        )
                );

        assertEquals(10.0, system.currentVector()[0].asDouble(), 1e-9);
        assertEquals(-10.0, system.currentVector()[1].asDouble(), 1e-9);
    }

    @Test
    public void sumsMultipleCurrentInjectionsOnSameNode() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("A", 0.0)
                ),
                List.of()
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "A",
                        List.of(
                                new CurrentInjection(
                                        "A",
                                        Real.fromDouble(3.0)
                                ),
                                new CurrentInjection(
                                        "A",
                                        Real.fromDouble(4.0)
                                )
                        )
                );

        assertEquals(7.0, system.currentVector()[0].asDouble(), 1e-9);
    }

    @Test
    public void rejectsCurrentInjectionForUnknownNode() {

        CalculationNetwork network = new CalculationNetwork(
                List.of(
                        node("A", 0.0)
                ),
                List.of()
        );

        try {
            new AdmittanceSystemBuilder().build(
                    network,
                    "A",
                    List.of(
                            new CurrentInjection(
                                    "missing",
                                    Real.fromDouble(1.0)
                            )
                    )
            );

            fail("Expected IllegalArgumentException");

        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("missing"));
        }
    }

}