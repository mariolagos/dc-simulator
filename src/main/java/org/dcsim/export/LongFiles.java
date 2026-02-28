package org.dcsim.export;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.DcSimPaths;
import org.longtable.LongFileWriter;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * LongFiles
 *  - Minimal bootstrap so DcSimApp can start the longtable writer from config.
 *  - Deterministic output: DcSimApp should pass DcSimPaths.OutputRoots.
 */
public final class LongFiles {

    private static volatile LongFileWriter WRITER = null;
    private static final Object LOCK = new Object();

    private LongFiles() {}

    /**
     * Legacy bootstrap (uses ConfigFactory.load()). Prefer bootstrapFromConfig(cfg, roots) from DcSimApp.
     */
    public static void bootstrapFromConfig() {
        bootstrapFromConfig(ConfigFactory.load(), null);
    }

    /** Legacy bootstrap. Prefer bootstrapFromConfig(cfg, roots) from DcSimApp. */
    public static void bootstrapFromConfig(Config cfg) {
        bootstrapFromConfig(cfg, null);
    }

    /**
     * Deterministic bootstrap: resolve longtable path via DcSimPaths.OutputRoots when available.
     * - If longfile.path is absolute: use as-is.
     * - If longfile.path is relative and roots != null: resolve under roots.resultsDir().
     * - If longfile.path missing and roots != null: use roots.longtablePath().
     * - Otherwise: fallback to "longtable.csv" (may be CWD-dependent; only for legacy/bench use).
     */
    public static void bootstrapFromConfig(Config cfg, DcSimPaths.OutputRoots roots) {
        if (WRITER != null) return;
        synchronized (LOCK) {
            if (WRITER != null) return;

            String project = cfg.hasPath("longfile.project") ? cfg.getString("longfile.project") : "dc-simulator";
            String scenario = cfg.hasPath("longfile.scenario") ? cfg.getString("longfile.scenario") : "default";
            String hash = cfg.hasPath("longfile.hash") ? cfg.getString("longfile.hash") : "nohash";
            boolean overwrite = cfg.hasPath("longfile.overwrite") && cfg.getBoolean("longfile.overwrite");

            Path finalPath;
            if (cfg.hasPath("longfile.path")) {
                Path raw = Paths.get(cfg.getString("longfile.path"));
                if (raw.isAbsolute()) {
                    finalPath = raw;
                } else if (roots != null) {
                    finalPath = roots.resultsDir().resolve(raw).normalize();
                } else {
                    finalPath = raw.normalize();
                }
            } else if (roots != null) {
                finalPath = roots.longtablePath();
            } else {
                finalPath = Paths.get("longtable.csv").normalize();
            }

            String csvPath = finalPath.toString();
            WRITER = LongFileWriter.forDcsim(csvPath, overwrite, project, scenario, hash, 0.0, "NET");

            System.out.println("[LongFiles] LongFileWriter ready at " + finalPath.toAbsolutePath()
                    + " project=" + project + " scenario=" + scenario + " hash=" + hash
                    + " overwrite=" + overwrite);
        }
    }

    /** Get the shared writer (after bootstrap). */
    public static LongFileWriter writer() {
        LongFileWriter w = WRITER;
        if (w == null) {
            throw new IllegalStateException("LongFiles not bootstrapped. Call LongFiles.bootstrapFromConfig(...) early in DcSimApp.");
        }
        return w;
    }

    /** Close quietly (optional on shutdown). */
    public static void closeQuietly() {
        LongFileWriter w = WRITER;
        if (w != null) {
            try { w.close(); } catch (Exception ignore) {}
        }
    }
}
