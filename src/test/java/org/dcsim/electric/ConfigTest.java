package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ConfigTest {
    public static void main(String[] args) {
        Config config = ConfigFactory.load();
        System.out.println("Top-level keys: " + config.root().keySet());
        System.out.println("grid exists? " + config.hasPath("grid"));
        System.out.println(config.getConfig("grid").root().render());
    }
}