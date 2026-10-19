package org.supply.loader;

import com.typesafe.config.Config;
import java.util.*;

/** Resolves traction current limits: timetable entry, then template, then system. */
public final class TrainCurrentLimitFactory {
    public Map<String, Double> build(Config traffic, double systemLimitA) {
        requireValid(systemLimitA, "system");
        Map<String, Double> out = new LinkedHashMap<>();
        Config templates = traffic.hasPath("templates") ? traffic.getConfig("templates") : null;
        if (templates != null) {
            for (String id : templates.root().keySet()) {
                Config template = templates.getObject(com.typesafe.config.ConfigUtil.joinPath(id)).toConfig();
                optional(template, "template " + id);
            }
        }
        if (!traffic.hasPath("timetable.trains")) return Map.of();
        for (Config train : traffic.getConfigList("timetable.trains")) {
            String id = train.getString("id");
            String templateId = train.hasPath("template_id") ? train.getString("template_id")
                    : train.getString("templateId");
            if (templates == null) throw new IllegalArgumentException("Missing templates for train " + id);
            Config template = templates.getConfig(templateId);
            Double templateLimit = optional(template, "template " + templateId);
            Double trainLimit = optional(train, "train " + id);
            double effective = trainLimit != null ? trainLimit : templateLimit != null ? templateLimit : systemLimitA;
            int count = train.getInt("count");
            if (count <= 0) throw new IllegalArgumentException("count must be > 0 for train " + id);
            for (int i = 0; i < count; i++) {
                String expandedId = count == 1 ? id : String.format(Locale.ROOT, "%s-%03d", id, i + 1);
                if (out.putIfAbsent(expandedId, effective) != null)
                    throw new IllegalArgumentException("Duplicate expanded train ID: " + expandedId);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Double optional(Config config, String owner) {
        if (!config.hasPathOrNull("i_train_max_A")) return null;
        double value = config.getDouble("i_train_max_A");
        requireValid(value, owner);
        return value;
    }

    private static void requireValid(double value, String owner) {
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("i_train_max_A must be finite and > 0 for " + owner);
    }
}
