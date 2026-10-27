package org.supply.loader;

import com.typesafe.config.*;
import java.util.*;

/** Resolves reusable station/switchgear input into ordinary explicit station objects. */
public final class SwitchGearTypeExpander {
    private SwitchGearTypeExpander() { }

    public static Config expand(Config grid) {
        if (!grid.hasPath("electricalObjects")) return grid;
        Config objects = grid.getConfig("electricalObjects");
        Config stationTypes = grid.hasPath("substationTypes") ? grid.getConfig("substationTypes") : ConfigFactory.empty();
        Config gearTypes = grid.hasPath("switchgearTypes") ? grid.getConfig("switchgearTypes") : ConfigFactory.empty();
        Map<String,Object> result = new LinkedHashMap<>(objects.root().unwrapped());
        for (String name : objects.root().keySet()) {
            Config local = objects.getConfig(name);
            if (!"SUBSTATION".equals(local.getString("kind"))) continue;
            Config defaults = ConfigFactory.empty();
            if (local.hasPath("type")) {
                String type = local.getString("type");
                defaults = named(stationTypes, type, "substation type for " + name);
                for (String forbidden : List.of("positions", "id", "kind", "type"))
                    if (defaults.hasPath(forbidden)) throw bad("Substation type must not define " + forbidden + ": " + type);
            }
            // A local type selection replaces the inherited wiring, not only its name.
            if (local.hasPath("switchgear_type")) defaults = defaults.withoutPath("switchgear");
            Config effective = local.withFallback(defaults);
            Config gear = effective.hasPath("switchgear") ? effective.getConfig("switchgear") : ConfigFactory.empty();
            if (effective.hasPath("switchgear_type")) {
                String type = effective.getString("switchgear_type");
                Config template = named(gearTypes, type, "switchgear type for " + name);
                if (template.hasPath("cable_overrides")) throw bad("Define cable_overrides on the station/substation type, not switchgearTypes." + type);
                gear = gear.withFallback(template);
            }
            if (!gear.isEmpty()) {
                gear = overrideCables(gear, name);
                effective = effective.withValue("switchgear", gear.root());
            }
            // Materialize station defaults so all downstream consumers see exactly the same spec.
            effective = effective.withoutPath("switchgear_type").withoutPath("type");
            result.put(name, effective.root().unwrapped());
        }
        return grid.withValue("electricalObjects", ConfigValueFactory.fromMap(result));
    }

    private static Config overrideCables(Config gear, String station) {
        if (!gear.hasPath("cable_overrides")) return gear;
        Config overrides = gear.getConfig("cable_overrides");
        Set<String> remaining = new HashSet<>(overrides.root().keySet());
        List<Map<String,Object>> cables = new ArrayList<>();
        for (Config cable : gear.getConfigList("connections")) {
            String id = cable.getString("id");
            if (remaining.remove(id)) {
                Config override = overrides.getConfig(id);
                for (String key : override.root().keySet())
                    if (!Set.of("length_m", "conductor_type", "multiplicity").contains(key))
                        throw bad("Cable override may change length_m, conductor_type or multiplicity only: " + station + "." + id + "." + key);
                cable = override.withFallback(cable);
            }
            cables.add(cable.root().unwrapped());
        }
        if (!remaining.isEmpty()) throw bad("Unknown cable overrides for " + station + ": " + new TreeSet<>(remaining));
        return gear.withValue("connections", ConfigValueFactory.fromIterable(cables)).withoutPath("cable_overrides");
    }

    private static Config named(Config types, String name, String label) {
        if (!name.matches("[\\p{L}\\p{Nd}_-]+") || !types.root().containsKey(name)) throw bad("Unknown " + label + ": " + name);
        return types.getConfig(name);
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
}
