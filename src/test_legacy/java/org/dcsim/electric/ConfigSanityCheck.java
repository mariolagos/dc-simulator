package org.dcsim.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ConfigSanityCheck {
    public static void main(String[] args) {
        Config config = ConfigFactory.parseResources("MinimalTest.conf").resolve();
        System.out.println(config.getConfig("dcsim.powerProfiles").root().render());
    }
}
