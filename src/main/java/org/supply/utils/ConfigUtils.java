package org.supply.utils;

import com.typesafe.config.Config;
import org.supply.math.Real;

public class ConfigUtils {
    public static String requireString(Config conf, String path) {
        if (!conf.hasPath(path)) {
            throw new IllegalArgumentException("Missing required field: " + path);
        }
        return conf.getString(path);
    }

    public static Real requirePositiveReal(Config conf, String path) {
        if (!conf.hasPath(path)) {
            throw new IllegalArgumentException("Missing required field: " + path);
        }
        double value = conf.getDouble(path);
        if (value <= 0) {
            throw new IllegalArgumentException("Required positive: " + path);
        }
        return Real.fromDouble(value);
    }

}
