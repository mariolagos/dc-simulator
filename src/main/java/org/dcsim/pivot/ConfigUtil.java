// src/main/java/org/dcsim/pivot/ConfigUtil.java
package org.dcsim.pivot;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

final class ConfigUtil {
    static final String ROOT_KEY = "dcsim.projects.root";
    static Config load() { return ConfigFactory.load(); }
    static Path resolveRoot(Config c) { return Paths.get(c.hasPath(ROOT_KEY) ? c.getString(ROOT_KEY) : "project"); }

    static Map<String,String> columnMap(Config c) {
        Map<String,String> m = new HashMap<>();
        if (c.hasPath("pivot.columns")) {
            Config cc = c.getConfig("pivot.columns");
            for (String k : cc.root().keySet()) m.put(k, cc.getString(k));
        }
        // defaults om inte satt
        m.putIfAbsent("time", "time_s");
        m.putIfAbsent("kind", "kind");
        m.putIfAbsent("id",   "id");
        m.putIfAbsent("V",    "V_V");
        m.putIfAbsent("I",    "I_A");
        m.putIfAbsent("P",    "P_W");
        m.putIfAbsent("req",  "req_W");
        m.putIfAbsent("pos",  "pos_m");
        m.putIfAbsent("speed","speed_mps");
        m.putIfAbsent("project","project");
        m.putIfAbsent("scenario","scenario");
        m.putIfAbsent("hash","hash_tag");
        m.putIfAbsent("VA","V_A_V");
        m.putIfAbsent("VB","V_B_V");
        m.putIfAbsent("P_loss","P_loss_W");
        return m;
    }
}
