package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationCategory;
import org.supply.domain.Load;
import org.supply.domain.SixTerminalRectifierTerminal;
import org.supply.model.GridModel;
import org.supply.domain.SixTerminalRectifierInstallation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GridModelLoaderTest {

    @Test
    public void loadsFixedLoadAndItsElectricalConnections() {
        Config config = ConfigFactory.parseString("""
                grid {
                  nodes = [
                    { node_id = "F_FL1", position_rwy = "1 0+500" }
                    { node_id = "R_FL1", position_rwy = "1 0+500" }
                  ]

                  lines = []

                  fixed_loads = [
                    {
                      id = "FL1"
                      position_rwy = "1 0+500"
                      power_W = 1000000
                    }
                  ]

                  power_installations = [
                    {
                      installation_id = "FL1"
                      installation_category = "FIXED_LOAD"
                    }
                  ]

                  installation_connections = [
                    {
                      installation_id = "FL1"
                      node_id = "F_FL1"
                      connection_type = "FEEDING"
                    }
                    {
                      installation_id = "FL1"
                      node_id = "R_FL1"
                      connection_type = "RETURN"
                    }
                  ]
                }
                """);

        GridModel model =
                new GridModelLoader().load(config);

        assertEquals(1, model.fixedLoads().size());

        Load.FixedLoad load =
                model.fixedLoads().get(0);

        assertEquals("FL1", load.id());
        assertEquals(500, load.position().getPositionM());
        assertEquals(1_000_000.0, load.powerW(), 1e-9);

        assertEquals(1, model.getInstallations().size());
        assertEquals(
                InstallationCategory.FIXED_LOAD,
                model.getInstallations().get(0).getInstallationCategory()
        );

        assertEquals(2, model.getInstallationConnections().size());

        assertTrue(
                model.getInstallationConnections().stream()
                        .anyMatch(c ->
                                c.getInstallationId().equals("FL1")
                                        && c.getNodeId().equals("F_FL1")
                                        && c.getConnectionType() == ConnectionType.FEEDING
                        )
        );

        assertTrue(
                model.getInstallationConnections().stream()
                        .anyMatch(c ->
                                c.getInstallationId().equals("FL1")
                                        && c.getNodeId().equals("R_FL1")
                                        && c.getConnectionType() == ConnectionType.RETURN
                        )
        );
    }

    @Test
    public void loadsSixTerminalRectifierInstallation() {
        Config config =
                ConfigFactory.parseString("""
                    grid {
                      nodes = [
                        {
                          node_id = "F_N_LEFT"
                          position_rwy = "200 4+000 U1"
                        },
                        {
                          node_id = "F_N_RIGHT"
                          position_rwy = "200 4+000 U1"
                        },
                        {
                          node_id = "F_S_LEFT"
                          position_rwy = "200 4+000 D1"
                        },
                        {
                          node_id = "F_S_RIGHT"
                          position_rwy = "200 4+000 D1"
                        },
                        {
                          node_id = "R_N"
                          position_rwy = "200 4+000 U1"
                        },
                        {
                          node_id = "R_S"
                          position_rwy = "200 4+000 D1"
                        }
                      ]

                      power_installations = [
                        {
                          installation_id = "SS0"
                          installation_category = SUBSTATION
                          installation_model =
                                  SIX_TERMINAL_RECTIFIER
                          enabled = true
                          emf_V = 3120.0
                          internal_resistance_ohm = 0.08
                          rectifier_type = DIODE

                          terminals {
                            NORTH_POWER_LEFT = "F_N_LEFT"
                            NORTH_POWER_RIGHT = "F_N_RIGHT"
                            SOUTH_POWER_LEFT = "F_S_LEFT"
                            SOUTH_POWER_RIGHT = "F_S_RIGHT"
                            NORTH_RETURN = "R_N"
                            SOUTH_RETURN = "R_S"
                          }
                        }
                      ]
                    }
                    """);

        GridModel model =
                new GridModelLoader().load(config);

        assertEquals(
                1,
                model.getInstallations().size()
        );
        assertEquals(
                1,
                model.getSixTerminalRectifierInstallations()
                        .size()
        );
        assertEquals(
                6,
                model.getInstallationConnections().size()
        );

        long feedingCount =
                model.getInstallationConnections().stream()
                        .filter(connection ->
                                connection.getConnectionType()
                                        == ConnectionType.FEEDING
                        )
                        .count();

        long returnCount =
                model.getInstallationConnections().stream()
                        .filter(connection ->
                                connection.getConnectionType()
                                        == ConnectionType.RETURN
                        )
                        .count();

        assertEquals(4L, feedingCount);
        assertEquals(2L, returnCount);

        SixTerminalRectifierInstallation installation =
                model.getSixTerminalRectifierInstallations()
                        .get(0);

        assertEquals("SS0", installation.installationId());
        assertEquals(
                "F_N_LEFT",
                installation.terminalNodeIds().get(
                        SixTerminalRectifierTerminal
                                .NORTH_POWER_LEFT
                )
        );
        assertEquals(
                "R_S",
                installation.terminalNodeIds().get(
                        SixTerminalRectifierTerminal
                                .SOUTH_RETURN
                )
        );
    }

    @Test
    public void rejectsMultipleConnectionsForLegacySubstation() {
        Config config =
                ConfigFactory.parseString("""
                    grid {
                      nodes = [
                        {
                          node_id = "F1"
                          position_rwy = "1 0+000"
                        },
                        {
                          node_id = "F2"
                          position_rwy = "1 0+000"
                        },
                        {
                          node_id = "R1"
                          position_rwy = "1 0+000"
                        }
                      ]

                      power_installations = [
                        {
                          installation_id = "SS1"
                          installation_category = SUBSTATION
                          enabled = true
                          emf_V = 750.0
                          internal_resistance_ohm = 0.01
                          rectifier_type = DIODE
                        }
                      ]

                      installation_connections = [
                        {
                          installation_id = "SS1"
                          node_id = "F1"
                          connection_type = FEEDING
                        },
                        {
                          installation_id = "SS1"
                          node_id = "F2"
                          connection_type = FEEDING
                        },
                        {
                          installation_id = "SS1"
                          node_id = "R1"
                          connection_type = RETURN
                        }
                      ]
                    }
                    """);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new GridModelLoader().load(config)
                );

        assertTrue(
                exception.getMessage().contains(
                        "expected FEEDING=1, RETURN=1"
                )
        );
    }

}