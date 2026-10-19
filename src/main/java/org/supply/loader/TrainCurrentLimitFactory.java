package org.supply.loader;

import com.typesafe.config.*;
import org.supply.domain.TrainCurrentCurve;
import java.util.*;

/** Resolves the complete traction-current characteristic: train, template, system. */
public final class TrainCurrentLimitFactory {
    public record Limits(Map<String, Double> maxima, Map<String, TrainCurrentCurve> curves) {
        public Limits { maxima = Map.copyOf(maxima); curves = Map.copyOf(curves); }
    }
    private record Profile(double maximumA, TrainCurrentCurve curve) { }

    /** Compatibility API for scalar-only callers. Use buildForStudy to retain curves. */
    public Map<String, Double> build(Config traffic, double systemLimitA) {
        requireValid(systemLimitA, "system");
        Limits limits = resolve(traffic, new Profile(systemLimitA, null));
        if (!limits.curves().isEmpty()) throw new IllegalArgumentException("Use buildForStudy to retain current curves");
        return limits.maxima();
    }

    public Limits buildForStudy(Config dcsim, double systemLimitA) {
        requireValid(systemLimitA, "system");
        Profile system = optional(dcsim.getConfig("grid"), "system", true);
        if (system == null) system = new Profile(systemLimitA, null);
        return resolve(dcsim.getConfig("traffic"), system);
    }

    private Limits resolve(Config traffic, Profile system) {
        Map<String, Double> maxima = new LinkedHashMap<>();
        Map<String, TrainCurrentCurve> curves = new LinkedHashMap<>();
        Config templates = traffic.hasPath("templates") ? traffic.getConfig("templates") : null;
        if (templates != null) for (String id : templates.root().keySet())
            optional(templates.getObject(ConfigUtil.joinPath(id)).toConfig(), "template " + id, false);
        if (!traffic.hasPath("timetable.trains")) return new Limits(maxima, curves);
        for (Config train : traffic.getConfigList("timetable.trains")) {
            String id = train.getString("id");
            String templateId = train.hasPath("template_id") ? train.getString("template_id") : train.getString("templateId");
            if (templates == null) throw new IllegalArgumentException("Missing templates for train " + id);
            Profile template = optional(templates.getConfig(templateId), "template " + templateId, false);
            Profile override = optional(train, "train " + id, false);
            Profile effective = override != null ? override : template != null ? template : system;
            int count = train.getInt("count");
            if (count <= 0) throw new IllegalArgumentException("count must be > 0 for train " + id);
            for (int i = 0; i < count; i++) {
                String expanded = count == 1 ? id : String.format(Locale.ROOT, "%s-%03d", id, i + 1);
                if (maxima.putIfAbsent(expanded, effective.maximumA()) != null)
                    throw new IllegalArgumentException("Duplicate expanded train ID: " + expanded);
                if (effective.curve() != null) curves.put(expanded, effective.curve());
            }
        }
        return new Limits(maxima, curves);
    }

    private static Profile optional(Config config, String owner, boolean system) {
        Double scalar = null;
        if (config.hasPathOrNull("i_train_max_A")) {
            scalar = config.getDouble("i_train_max_A"); requireValid(scalar, owner);
        }
        if (!config.hasPathOrNull("i_train_limit_curve"))
            return scalar == null ? null : new Profile(scalar, null);
        if (scalar != null && !system) throw new IllegalArgumentException(
                "Specify either i_train_max_A or i_train_limit_curve, not both, for " + owner);
        List<TrainCurrentCurve.Point> points = new ArrayList<>();
        for (ConfigValue value : config.getList("i_train_limit_curve")) {
            if (!(value instanceof ConfigList pair) || pair.size() != 2
                    || !(pair.get(0).unwrapped() instanceof Number u)
                    || !(pair.get(1).unwrapped() instanceof Number i))
                throw new IllegalArgumentException("i_train_limit_curve requires [voltage_V, current_A] pairs for " + owner);
            points.add(new TrainCurrentCurve.Point(u.doubleValue(), i.doubleValue()));
        }
        try {
            TrainCurrentCurve curve = new TrainCurrentCurve(points);
            return new Profile(curve.maximumA(), curve);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid i_train_limit_curve for " + owner + ": " + e.getMessage(), e);
        }
    }

    private static void requireValid(double value, String owner) {
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("i_train_max_A must be finite and > 0 for " + owner);
    }
}
