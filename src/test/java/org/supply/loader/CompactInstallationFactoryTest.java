package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.domain.Node;
import org.supply.domain.SixTerminalRectifierTerminal;
import org.supply.model.GridModel;
import org.supply.validation.GridModelValidator;

import static org.junit.Assert.*;

public class CompactInstallationFactoryTest {
    private static String station(String extra) {
        return "power_installations = [{ installation_id=SS1, installation_category=SUBSTATION, "
                + "installation_model=SIX_TERMINAL_RECTIFIER, emf_V=750, "
                + "internal_resistance_ohm=0.01, rectifier_type=DIODE, " + extra + " }]";
    }

    @Test public void compactAndExplicitDescriptionsProduceTheSameDomainModel() {
        InstallationFactory factory = new InstallationFactory();
        Config compact = ConfigFactory.parseString(station("position_rwy=\"101 1+000 U\""));
        GridModel generated = new GridModel();
        factory.prepareTerminalNodes(generated, compact);
        factory.build(generated, compact);
        new GridModelValidator().validate(generated);

        var terminals = generated.getSixTerminalRectifierInstallations().get(0).terminalNodeIds();
        StringBuilder explicitTerminals = new StringBuilder("terminals {\n");
        GridModel explicit = new GridModel();
        for (SixTerminalRectifierTerminal terminal : SixTerminalRectifierTerminal.values()) {
            String id = terminals.get(terminal);
            explicit.addNode(new Node(id, "101 1+000 U"));
            explicitTerminals.append(terminal.name()).append("=\"").append(id).append("\"\n");
        }
        explicitTerminals.append('}');
        Config legacy = ConfigFactory.parseString(station(explicitTerminals.toString()));
        factory.prepareTerminalNodes(explicit, legacy);
        factory.build(explicit, legacy);
        new GridModelValidator().validate(explicit);
        assertEquals(generated.getSixTerminalRectifierInstallations(),
                explicit.getSixTerminalRectifierInstallations());
        assertEquals(6, generated.getNodes().size());
        assertEquals(6, generated.getInstallationConnections().size());
        assertEquals(6, explicit.getInstallationConnections().size());
        for (var node : generated.getNodes())
            assertEquals(node.getPositionRwy(), explicit.getNode(node.getNodeId()).getPositionRwy());
    }

    @Test public void terminalObjectsOverrideNodeIdsAndPositions() {
        Config config = ConfigFactory.parseString(station("""
            position_rwy="101 1+000 U"
            terminals {
              NORTH_POWER_LEFT { node_id=F_CUSTOM }
              NORTH_POWER_RIGHT {}
              SOUTH_POWER_LEFT { position_rwy="101 1+000 D" }
              SOUTH_POWER_RIGHT { position_rwy="101 1+000 D" }
              NORTH_RETURN {}
              SOUTH_RETURN { position_rwy="101 1+000 D" }
            }
            """));
        GridModel model = new GridModel();
        InstallationFactory factory = new InstallationFactory();
        factory.prepareTerminalNodes(model, config);
        factory.build(model, config);
        assertEquals("101 1+000 U", model.getNode("F_CUSTOM").getPositionRwy());
        assertEquals("101 1+000 D", model.getNode("F_SS1_C").getPositionRwy());
        assertEquals("F_CUSTOM", model.getSixTerminalRectifierInstallations().get(0)
                .terminalNodeIds().get(SixTerminalRectifierTerminal.NORTH_POWER_LEFT));
    }

    @Test public void generatedNodesCannotOverwriteExistingNodes() {
        GridModel model = new GridModel();
        model.addNode(new Node("F_SS1_A", "101 0+000 U"));
        Config config = ConfigFactory.parseString(station("position_rwy=\"101 1+000 U\""));
        try {
            new InstallationFactory().prepareTerminalNodes(model, config);
            fail("Expected duplicate generated node to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("F_SS1_A"));
            assertEquals("101 0+000 U", model.getNode("F_SS1_A").getPositionRwy());
        }
    }

    @Test public void partialTerminalMapsAreRejectedInsteadOfGuessingConnections() {
        Config config = ConfigFactory.parseString(station(
                "position_rwy=\"101 1+000 U\", terminals { NORTH_POWER_LEFT {} }"));
        try {
            new InstallationFactory().prepareTerminalNodes(new GridModel(), config);
            fail("Expected missing terminal to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing terminal"));
        }
    }
}
