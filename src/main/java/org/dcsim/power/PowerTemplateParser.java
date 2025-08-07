package org.dcsim.power;

import com.typesafe.config.*;
import org.dcsim.PowerPoint;

import java.util.*;

public class PowerTemplateParser {

    public static Map<String, List<PowerPoint>> parse(Config rootConfig) {
        Map<String, List<PowerPoint>> templates = new HashMap<>();

        for (Map.Entry<String, ConfigValue> entry : rootConfig.root().entrySet()) {
            String templateId = entry.getKey();
            Config trainProfile = rootConfig.getConfig(templateId);
            List<? extends Config> pointsConf = trainProfile.getConfigList("points");

            List<PowerPoint> points = new ArrayList<>();
            for (Config c : pointsConf) {
                double time = c.getDouble("time");
                String pos = c.getString("position");
                double speed = c.getDouble("speed");
                double power = c.getDouble("power");

                PowerPoint p = new PowerPoint(time, pos, speed, power, 0., 0.);
                points.add(p);
            }

            templates.put(templateId, points);
            System.out.printf("Read power profile for %s: %d points%n", templateId, points.size());
        }

        return templates;
    }
}
