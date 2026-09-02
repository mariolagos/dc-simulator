package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.Route;

import java.util.ArrayList;
import java.util.List;

public final class RouteFactory {

    public List<Route> build(Config traffic) {
        if (!traffic.hasPath("routes")) {
            return List.of();
        }

        Config routesConfig = traffic.getConfig("routes");
        List<Route> routes = new ArrayList<>();

        for (String routeId : routesConfig.root().keySet()) {
            Config routeConfig = routesConfig.getConfig(routeId);

            routes.add(new Route(
                    routeId,
                    routeConfig.getStringList("feeding_lines"),
                    routeConfig.getStringList("return_lines")
            ));
        }

        return List.copyOf(routes);
    }
}