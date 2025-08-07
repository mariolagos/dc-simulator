package org.dcsim.electric;
import com.typesafe.config.*;

public class HoconSanityCheck {
    public static void main(String[] args) {
        Config config = ConfigFactory.parseResources("MinimalTest.conf").resolve();
        System.out.println("=== FULL CONFIG ===");
        System.out.println(config.root().render());
        System.out.println("=== dcsim Section ===");
        System.out.println(config.getConfig("dcsim").root().render());
    }
}
