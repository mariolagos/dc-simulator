package org.dcsim.electric;

import com.typesafe.config.*;

import java.io.File;

public class HoconFileTest {
    public static void main(String[] args) {
        File file = new File("src/test/resources/MinimalTest.conf");

        if (!file.exists()) {
            System.err.println("Error File not found: " + file.getAbsolutePath());
            return;
        }

        Config config = ConfigFactory.parseFile(file).resolve();

        if (config.hasPath("dcsim")) {
            System.out.println("Success dcsim section found!");
            System.out.println(config.getConfig("dcsim").root().render(ConfigRenderOptions.defaults().setComments(false)));
        } else {
            System.err.println("Error dcsim section NOT found");
        }
    }
}
