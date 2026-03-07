package org.dcsim;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigResolveOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class DcSimScenarioLoader {
    private DcSimScenarioLoader() {
    }

    /**
     * Loads and resolves the full scenario config (with fallback to classpath).
     */
    public static Config loadScenarioConfig(Path confFile) {
        ConfigParseOptions parseOpts = ConfigParseOptions.defaults().setAllowMissing(false);
        ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                .setAllowUnresolved(false)
                .setUseSystemEnvironment(true);

        return ConfigFactory
                .parseFileAnySyntax(confFile.toFile(), parseOpts)
                .withFallback(ConfigFactory.load())
                .resolve(resolveOpts);
    }

    /**
     * Convenience: returns the dcsim sub-config (and fails with clear error if missing).
     */
    public static Config requireDcsim(Config scenario, Path confFile) {
        if (!scenario.hasPath("dcsim")) {
            throw new IllegalArgumentException("Top-level 'dcsim' section is missing in: " + confFile.toAbsolutePath());
        }
        return scenario.getConfig("dcsim");
    }

    /**
     * Accepts a file or directory (dir -> scenario.conf). Mirrors DcSimApp behavior.
     */
    public static Path resolveConfArg(String arg) {

        Path conf = Paths.get(arg);

        if (!conf.toString().endsWith(".conf") && !conf.toString().endsWith(".hocon")) {
            throw new IllegalArgumentException("Config must be a .conf or .hocon file: " + conf);
        }

        // resolve relative paths against CWD
        if (!conf.isAbsolute()) {
            conf = Paths.get("").toAbsolutePath().resolve(conf);
        }

        conf = conf.normalize();

        if (!Files.exists(conf)) {
            throw new IllegalArgumentException("Config not found: " + conf);
        }

        if (!Files.isRegularFile(conf)) {
            throw new IllegalArgumentException("Config is not a file: " + conf);
        }

        return conf;
    }
    public static void applyVerboseAllIfConfigured(Config scenario) {
        boolean verboseAll = false;
        try {
            if (scenario.hasPath("dcsim.verbose.all")) {
                verboseAll = scenario.getBoolean("dcsim.verbose.all");
            } else if (scenario.hasPath("dcsim.verbose") && scenario.getConfig("dcsim.verbose").hasPath("all")) {
                verboseAll = scenario.getConfig("dcsim.verbose").getBoolean("all");
            }
        } catch (Exception ignore) {
        }

        if (verboseAll) {
            System.setProperty("dcsim.verbose.all", "true");
            System.out.println("[CONF] Verbose mode enabled (dcsim.verbose.all=true)");
        }

    }

    public static void logConfigSummary(Config dcsim, Path confFile) {
        // ---- 4) Logga vad som faktiskt lästes (bra vid “<MISSING>") ----
        ConfigRenderOptions compactPretty = ConfigRenderOptions.concise().setFormatted(true).setJson(false);
        System.out.println("[CONF] Loaded from: " + confFile.toAbsolutePath());
        System.out.println("[CONF] dcsim.trains.defaults = " +
                (dcsim.hasPath("trains.defaults")
                        ? dcsim.getConfig("trains.defaults").root().render(compactPretty)
                        : "<MISSING>"));
        System.out.println("[CONF] dcsim.trains.overrides = " +
                (dcsim.hasPath("trains.overrides")
                        ? dcsim.getConfig("trains.overrides").root().render(compactPretty)
                        : "<MISSING>"));
        System.out.println("[CONF] dcsim.grid.substations = " +
                (dcsim.hasPath("grid.substations")
                        ? dcsim.getConfig("grid").getList("substations").render(compactPretty)
                        : "<MISSING>"));
        System.out.println("[CONF] dcsim.export = " +
                (dcsim.hasPath("export")
                        ? dcsim.getConfig("export").root().render(compactPretty)
                        : "<MISSING>"));

    }
}
