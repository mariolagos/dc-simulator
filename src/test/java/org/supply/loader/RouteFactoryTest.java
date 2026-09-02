package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.domain.Route;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class RouteFactoryTest {

    @Test
    public void loadsRoutesFromTrafficConfig() {
        Config traffic = ConfigFactory.parseString("""
                routes {
                  U {
                    feeding_lines = [
                      "F_START_U-F1",
                      "F1-F_END_U"
                    ]
                    return_lines = [
                      "R_START_U-R1",
                      "R1-R_END_U"
                    ]
                  }

                  D {
                    feeding_lines = [
                      "F_START_D-F1",
                      "F1-F_END_D"
                    ]
                    return_lines = [
                      "R_START_D-R1",
                      "R1-R_END_D"
                    ]
                  }
                }
                """);

        List<Route> routes =
                new RouteFactory().build(traffic);

        assertEquals(2, routes.size());

        Route up = routes.stream()
                .filter(r -> r.id().equals("U"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                List.of("F_START_U-F1", "F1-F_END_U"),
                up.feedingLineIds()
        );

        assertEquals(
                List.of("R_START_U-R1", "R1-R_END_U"),
                up.returnLineIds()
        );
    }
}