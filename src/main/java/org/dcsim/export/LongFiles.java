package org.dcsim.export;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.longtable.LongFileWriter;

/**
 * LongFiles
 * - Minimal bootstrap so DcSimApp can start the longtable writer from config
 * - Creates LongFileWriter (via DcsimSink) and writes RUN meta directly (t0 = 0.0, stage="NET")
 * - Leaves the rest of the system untouched.
 */
public final class LongFiles {

    private static volatile LongFileWriter WRITER = null;
    private static final Object LOCK = new Object();

    private LongFiles() {}

    /**
     * Bootstrap from application.conf (or given Config). Called ONCE early in the app's lifecycle.
     */
    public static void bootstrapFromConfig() {
        bootstrapFromConfig(ConfigFactory.load());
    }

    public static void bootstrapFromConfig(Config cfg) {
        if (WRITER != null) return;

        synchronized (LOCK) {
            if (WRITER != null) return;

            // --- Parameters ---
            String project = cfg.hasPath("longfile.project")
                    ? cfg.getString("longfile.project")
                    : "dc-simulator";

            String scenario = cfg.hasPath("longfile.scenario")
                    ? cfg.getString("longfile.scenario")
                    : "default";

            String hash = cfg.hasPath("longfile.hash")
                    ? cfg.getString("longfile.hash")
                    : "nohash";

            boolean overwrite = cfg.hasPath("longfile.overwrite")
                    && cfg.getBoolean("longfile.overwrite");

            // --- Deterministic path handling ---
            String pathFromConfig = cfg.hasPath("longfile.path")
                    ? cfg.getString("longfile.path")
                    : "longtable.csv";   // no "output/"

            java.nio.file.Path rawPath = java.nio.file.Paths.get(pathFromConfig);
            java.nio.file.Path finalPath;

            if (rawPath.isAbsolute()) {
                finalPath = rawPath;
            } else {

                String resultsRootStr = null;

                if (cfg.hasPath("dcsim.paths.resultsDir"))
                    resultsRootStr = cfg.getString("dcsim.paths.resultsDir");
                else if (cfg.hasPath("dcsim.resultsDir"))
                    resultsRootStr = cfg.getString("dcsim.resultsDir");

                if (resultsRootStr == null || resultsRootStr.trim().isEmpty()) {
                    throw new IllegalStateException(
                            "[LongFiles] longfile.path is relative (" + rawPath + ") but no results root configured.");
                }

                java.nio.file.Path resultsRoot =
                        java.nio.file.Paths.get(resultsRootStr);

                if (!resultsRoot.isAbsolute()) {
                    throw new IllegalStateException(
                            "[LongFiles] results root must be ABSOLUTE. Got: " + resultsRoot);
                }

                finalPath = resultsRoot
                        .resolve(project)
                        .resolve(scenario)
                        .resolve(rawPath)
                        .normalize();
            }

            String csvPath = finalPath.toString();

            WRITER = LongFileWriter.forDcsim(
                    csvPath,
                    overwrite,
                    project,
                    scenario,
                    hash,
                    0.0,
                    "NET"
            );

            System.out.println("[LongFiles] LongFileWriter ready at "
                    + finalPath.toAbsolutePath()
                    + " project=" + project
                    + " scenario=" + scenario
                    + " hash=" + hash
                    + " overwrite=" + overwrite);
        }
    }

    private static String firstPresentString(Config cfg, String... keys) {
        for (String k : keys) {
            if (cfg.hasPath(k)) {
                try {
                    String v = cfg.getString(k);
                    if (v != null) return v;
                } catch (Exception ignore) {
                    // ignore non-string types
                }
            }
        }
        return null;
    }

    /** Get the shared longtable printer (after bootstrap). */
    public static LongFileWriter writer() {
        LongFileWriter w = WRITER;
        if (w == null) {
            throw new IllegalStateException("LongFiles not bootstrapped. Call LongFiles.bootstrapFromConfig() early in DcSimApp.");
        }
        return w;
    }

    /** Close gracefully (optional on shutdown). */
    public static void closeQuietly() {
        LongFileWriter w = WRITER;
        if (w != null) {
            try { w.close(); } catch (Exception ignore) {}
        }
    }
}
