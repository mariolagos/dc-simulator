package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.nio.file.Path;

public final class DcSimConfigLoader {

    private DcSimConfigLoader() {
    }

    public static Config loadScenarioConfig(Path confFile) {
        return ConfigFactory.parseFile(confFile.toFile()).resolve();
    }

    public static Config requireDcsim(Config scenario, Path confFile) {
        if (!scenario.hasPath("dcsim")) {
            throw new IllegalArgumentException("Missing required root object 'dcsim' in " + confFile);
        }
        return scenario.getConfig("dcsim");
    }
}