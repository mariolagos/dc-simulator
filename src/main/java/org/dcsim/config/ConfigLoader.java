// package: org.dcsim.config
package org.dcsim.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;

/**
 * Parses application.conf (HOCON) into AppConfig.
 * For now we only extract simulationControl so DcSimApp can run the clock.
 * Extend to map track/grid/traffic/powerProfiles as you wire the loader.
 */
public final class ConfigLoader {

    private ConfigLoader() { }

    public static AppConfig load(String confPath) {
        Config root = ConfigFactory.parseFile(new File(confPath)).resolve();
        Config dcsim = root.getConfig("dcsim");

        // --- simulationControl (authoritative in USER_GUIDE) ---
        Config sc = dcsim.getConfig("simulationControl");
        double tickDuration = sc.getDouble("tickDuration");
        String simulationStart = sc.getString("simulationStart");
        String simulationEnd   = sc.getString("simulationEnd");

        AppConfig.SimulationControl simCtl =
                new AppConfig.SimulationControl(tickDuration, simulationStart, simulationEnd);

        // TODO: parse and attach the rest:
        // - track.stations
        // - grid.groundNodeId, nodes, lines, substations, connectionPoints
        // - traffic.timetable + templates
        // - powerProfiles (templates, legs, auxiliaryPower, etc.)

        return new AppConfig(simCtl);
    }
}
