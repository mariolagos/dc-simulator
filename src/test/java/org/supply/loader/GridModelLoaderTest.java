package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.domain.ConnectionType;
import org.supply.domain.InstallationType;
import org.supply.domain.Load;
import org.supply.model.GridModel;

import static org.junit.Assert.assertEquals;
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
                      installation_type = "FIXED_LOAD"
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
                InstallationType.FIXED_LOAD,
                model.getInstallations().get(0).getInstallationType()
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
}