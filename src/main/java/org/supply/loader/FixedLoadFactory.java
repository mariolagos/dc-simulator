package org.supply.loader;

import org.supply.track.RwyCoordinate;
import com.typesafe.config.Config;
import org.supply.domain.Load;
import org.supply.model.GridModel;

import java.util.List;

public class FixedLoadFactory {

    public void build(GridModel model, Config gridConfig) {
        if (!gridConfig.hasPath("fixed_loads")) {
            return;
        }

        List<? extends Config> loads =
                gridConfig.getConfigList("fixed_loads");

        for (Config loadConfig : loads) {
            Load.FixedLoad load = new Load.FixedLoad(
                    loadConfig.getString("id"),
                    parsePosition(loadConfig.getString("position_rwy")),
                    loadConfig.getDouble("power_W")
            );

            model.addFixedLoad(load);
        }
    }

    private RwyCoordinate parsePosition(String value) {
        // Example: "1 3+000"
        String[] parts = value.trim().split(" ");

        String section = parts[0];

        String[] kmMeter = parts[1].split("\\+");

        String km = kmMeter[0];
        int meter = Integer.parseInt(kmMeter[1]);

        return new RwyCoordinate(
                section,
                km,
                meter,
                ""
        );
    }
}
