package org.dcsim.power;

import com.typesafe.config.Config;
import org.dcsim.PowerPoint;

import java.io.File;
import java.util.*;

public class TrainTemplateInstantiator {

    public record TrainInstance(String id, PowerProfile profile, int fromNode, int toNode) {}

    public static List<TrainInstance> instantiate(Config config) {
        List<TrainInstance> instances = new ArrayList<>();

        String basePath = config.getConfig("powerTemplates").getString("basePath");
        List<? extends Config> trainConfigs = config.getConfigList("trains");

        for (Config trainCfg : trainConfigs) {
            String id = trainCfg.getString("id");
            String template = trainCfg.getString("template");
            double departure = trainCfg.getDouble("departure");

            // Optional: from/to nodes (default to 2 -> 1)
            int from = trainCfg.hasPath("from") ? trainCfg.getInt("from") : 2;
            int to = trainCfg.hasPath("to") ? trainCfg.getInt("to") : 1;

            File templateDir = new File(basePath, template);
            List<PowerPoint> original = ExcelProfileReader.read(templateDir);

            // Shift time
            List<PowerPoint> shifted = new ArrayList<>();
            for (PowerPoint p : original) {
                shifted.add(p.shifted(departure));
            }

            PowerProfile profile = new PowerProfile(shifted);
            instances.add(new TrainInstance(id, profile, from, to));
        }

        return instances;
    }
}
