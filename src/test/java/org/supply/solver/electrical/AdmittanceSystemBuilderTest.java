package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class AdmittanceSystemBuilderTest {

    @Test
    public void buildsNodeIndexOrderingFromActiveNodesInNetworkOrder() {
        CalculationNetwork network = network(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0),
                        node("C", 200.0)
                ),
                List.of(
                        branch("b1", "A", "B", 1.0),
                        branch("b2", "B", "C", 1.0)
                )
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "A");

        assertEquals(3, system.size());
        assertEquals(0, system.nodeIndex("A"));
        assertEquals(1, system.nodeIndex("B"));
        assertEquals(2, system.nodeIndex("C"));
    }

    @Test
    public void excludesInactiveNodesFromAdmittanceSystem() {
        CalculationNetwork network = network(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0),
                        node("C", 200.0)
                ),
                List.of(
                        branch("b1", "A", "B", 1.0)
                )
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "A");

        assertEquals(2, system.size());
        assertEquals(0, system.nodeIndex("A"));
        assertEquals(1, system.nodeIndex("B"));

        try {
            system.nodeIndex("C");
            fail("Expected unknown node C");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("C"));
        }
    }

    @Test
    public void stampsPassiveBranchConductanceIntoMatrix() {
        CalculationNetwork network = network(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0)
                ),
                List.of(
                        branch("branch-1", "A", "B", 2.0)
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
    public void stampsSubstationNortonEquivalentIntoMatrixAndCurrentVector() {
        List<CalculationNode> nodes = List.of(
                node("F1", 0.0),
                node("R1", 0.0)
        );

        SubstationElement substation = new SubstationElement(
                "SS1",
                "F1",
                "R1",
                Real.fromDouble(780.0),
                Real.fromDouble(0.1)
        );

        CalculationNetwork network = new CalculationNetwork(
                nodes,
                List.of(),
                List.of(),
                List.of(substation)
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "R1",
                        List.of(
                                new CurrentInjection("F1", Real.fromDouble(0.0)),
                                new CurrentInjection("R1", Real.fromDouble(0.0))
                        )
                );

        Real[][] g = system.conductanceMatrix();
        Real[] j = system.currentVector();

        assertEquals(10.0, g[0][0].asDouble(), 1e-9);
        assertEquals(-10.0, g[0][1].asDouble(), 1e-9);
        assertEquals(-10.0, g[1][0].asDouble(), 1e-9);
        assertEquals(10.0, g[1][1].asDouble(), 1e-9);

        assertEquals(7800.0, j[0].asDouble(), 1e-9);
        assertEquals(-7800.0, j[1].asDouble(), 1e-9);
    }

    @Test
    public void exposesReferenceNodeIndex() {
        CalculationNetwork network = network(
                List.of(
                        node("feed", 0.0),
                        node("return", 100.0)
                ),
                List.of(
                        branch("b1", "feed", "return", 1.0)
                )
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(network, "return");

        assertEquals("return", system.referenceNodeId());
        assertEquals(1, system.referenceNodeIndex());
    }

    @Test
    public void rejectsUnknownReferenceNode() {
        CalculationNetwork network = network(
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
                List.of(),
                List.of(),
                List.of()
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "B",
                        List.of(
                                new CurrentInjection("A", Real.fromDouble(10.0)),
                                new CurrentInjection("B", Real.fromDouble(-10.0))
                        )
                );

        assertEquals(10.0, system.currentVector()[0].asDouble(), 1e-9);
        assertEquals(-10.0, system.currentVector()[1].asDouble(), 1e-9);
    }

    @Test
    public void sumsMultipleCurrentInjectionsOnSameNode() {
        CalculationNetwork network = new CalculationNetwork(
                List.of(node("A", 0.0)),
                List.of(),
                List.of(),
                List.of()
        );

        AdmittanceSystem system =
                new AdmittanceSystemBuilder().build(
                        network,
                        "A",
                        List.of(
                                new CurrentInjection("A", Real.fromDouble(3.0)),
                                new CurrentInjection("A", Real.fromDouble(4.0))
                        )
                );

        assertEquals(7.0, system.currentVector()[0].asDouble(), 1e-9);
    }

    @Test
    public void rejectsCurrentInjectionForUnknownNode() {
        CalculationNetwork network = new CalculationNetwork(
                List.of(node("A", 0.0)),
                List.of(),
                List.of(),
                List.of()
        );

        try {
            new AdmittanceSystemBuilder().build(
                    network,
                    "A",
                    List.of(new CurrentInjection("missing", Real.fromDouble(1.0)))
            );

            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("missing"));
        }
    }

    @Test
    public void preservesMatrixIndicesAcrossRepeatedBuilds() {
        CalculationNetwork network = network(
                List.of(
                        node("A", 0.0),
                        node("B", 100.0),
                        node("C", 200.0)
                ),
                List.of(
                        branch("b1", "A", "B", 1.0),
                        branch("b2", "B", "C", 1.0)
                )
        );

        AdmittanceSystem system1 =
                new AdmittanceSystemBuilder().build(network, "A");

        AdmittanceSystem system2 =
                new AdmittanceSystemBuilder().build(network, "A");

        assertEquals(system1.size(), system2.size());

        for (int i = 0; i < system1.size(); i++) {
            assertEquals(system1.nodeIds().get(i), system2.nodeIds().get(i));
            assertEquals(
                    system1.nodeIndex(system1.nodeIds().get(i)),
                    system2.nodeIndex(system2.nodeIds().get(i))
            );
        }
    }

    private static CalculationNetwork network(
            List<CalculationNode> nodes,
            List<CalculationBranch> branches
    ) {
        List<ElectricalElement> elements = new ArrayList<>();
        elements.addAll(branches);

        return new CalculationNetwork(
                nodes,
                branches,
                List.of(),
                elements
        );
    }

    private static CalculationBranch branch(
            String id,
            String from,
            String to,
            double resistanceOhm
    ) {
        return new CalculationBranch(
                id,
                id,
                from,
                to,
                Real.fromDouble(resistanceOhm)
        );
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
}