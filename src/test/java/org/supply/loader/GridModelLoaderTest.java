package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.model.GridModel;

import static org.junit.Assert.*;

public class GridModelLoaderTest {

    private final GridModelLoader loader = new GridModelLoader();

    @Test
    public void loads_complete_grid_section() {
        Config config = ConfigFactory.parseString("""
            grid {
              nodes = [
                { node_id = "1", position_rwy = "23 0+000 U" },
                { node_id = "2", position_rwy = "23 1+200 U" },
                { node_id = "3", position_rwy = "23 1+200 D" }
              ]

              lines = [
                { node_from_id = "1", node_to_id = "2", resistance_ohm_per_m = 0.00012 }
              ]

              power_installations = [
                {
                  installation_id = "PS1"
                  installation_type = SUBSTATION
                  emf_V = 750.0
                  internal_resistance_ohm = 0.02
                  rectifier_type = DIODE
                },
                {
                  installation_id = "P1"
                  installation_type = POINT
                  rectifier_type = DIODE
                }
              ]

              installation_connections = [
                { installation_id = "PS1", node_id = "1", connection_type = FEEDING },
                { installation_id = "PS1", node_id = "3", connection_type = RETURN },
                { installation_id = "P1",  node_id = "2", connection_type = FEEDING }
              ]
            }
        """);

        GridModel model = loader.load(config);

        assertEquals(3, model.getNodes().size());
        assertEquals(1, model.getLines().size());
        assertEquals(2, model.getInstallations().size());
        assertEquals(3, model.getInstallationConnections().size());
    }

    @Test
    public void fails_when_line_references_unknown_node() {
        Config config = ConfigFactory.parseString("""
            grid {
              nodes = [
                { node_id = "1", position_rwy = "23 0+000 U" }
              ]
              lines = [
                { node_from_id = "1", node_to_id = "99", resistance_ohm_per_m = 0.00012 }
              ]
            }
        """);

        try {
            loader.load(config);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Node"));
        }
    }

    @Test
    public void fails_when_duplicate_installation_id() {
        Config config = ConfigFactory.parseString("""
            grid {
              nodes = [
                { node_id = "1", position_rwy = "23 0+000 U" }
              ]
              power_installations = [
                {
                  installation_id = "PS1"
                  installation_type = SUBSTATION
                  emf_V = 750.0
                  internal_resistance_ohm = 0.02
                  rectifier_type = DIODE
                },
                {
                  installation_id = "PS1"
                  installation_type = POINT
                  rectifier_type = DIODE
                }
              ]
            }
        """);

        try {
            loader.load(config);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Duplicate"));
        }
    }

    @Test
    public void fails_when_substation_missing_emf() {
        Config config = ConfigFactory.parseString("""
            grid {
              nodes = [
                { node_id = "1", position_rwy = "23 0+000 U" },
                { node_id = "2", position_rwy = "23 1+200 D" }
              ]
              power_installations = [
                {
                  installation_id = "PS1"
                  installation_type = SUBSTATION
                  internal_resistance_ohm = 0.02
                  rectifier_type = DIODE
                }
              ]
              installation_connections = [
                { installation_id = "PS1", node_id = "1", connection_type = FEEDING },
                { installation_id = "PS1", node_id = "2", connection_type = RETURN }
              ]
            }
        """);

        try {
            loader.load(config);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("emf"));
        }
    }

    @Test
    public void fails_when_substation_missing_return_connection() {
        Config config = ConfigFactory.parseString("""
            grid {
              nodes = [
                { node_id = "1", position_rwy = "23 0+000 U" },
                { node_id = "2", position_rwy = "23 1+200 D" }
              ]
              power_installations = [
                {
                  installation_id = "PS1"
                  installation_type = SUBSTATION
                  emf_V = 750.0
                  internal_resistance_ohm = 0.02
                  rectifier_type = DIODE
                }
              ]
              installation_connections = [
                { installation_id = "PS1", node_id = "1", connection_type = FEEDING }
              ]
            }
        """);

        try {
            loader.load(config);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("RETURN"));
        }
    }

    @Test
    public void allows_point_without_emf_and_internal_resistance() {
        Config config = ConfigFactory.parseString("""
            grid {
              nodes = [
                { node_id = "1", position_rwy = "23 0+000 U" }
              ]
              power_installations = [
                {
                  installation_id = "P1"
                  installation_type = POINT
                  rectifier_type = DIODE
                }
              ]
              installation_connections = [
                { installation_id = "P1", node_id = "1", connection_type = FEEDING }
              ]
            }
        """);

        GridModel model = loader.load(config);

        assertEquals(1, model.getInstallations().size());
        assertEquals(1, model.getInstallations().size());
    }
}