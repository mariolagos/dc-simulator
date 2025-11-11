package org.dcsim.export;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.longtable.LongFileWriter;

/**
 * LongFiles
 *  - Minimal bootstrap så DcSimApp kan starta longtable-skrivaren från config
 *  - Skapar LongFileWriter (via DcsimSink) och skriver RUN-meta direkt (t0 = 0.0, stage="NET")
 *  - Lämnar resten av systemet orört.
 */
public final class LongFiles {

    private static volatile LongFileWriter WRITER = null;
    private static final Object LOCK = new Object();

    private LongFiles() {}

    /** Bootstrap från application.conf (eller given Config). Kallas EN gång tidigt i appens livscykel. */
    public static void bootstrapFromConfig() {
        bootstrapFromConfig(ConfigFactory.load());
    }

    public static void bootstrapFromConfig(Config cfg) {
        if (WRITER != null) return;
        synchronized (LOCK) {
            if (WRITER != null) return;

            // Läs parametrar – byt nycklar här om dina paths/keys heter annat
            String project  = cfg.hasPath("longfile.project")  ? cfg.getString("longfile.project")  : "dc-simulator";
            String scenario = cfg.hasPath("longfile.scenario") ? cfg.getString("longfile.scenario") : "default";
            String hash     = cfg.hasPath("longfile.hash")     ? cfg.getString("longfile.hash")     : "nohash";
            String csvPath  = cfg.hasPath("longfile.path")     ? cfg.getString("longfile.path")     : "output/longtable.csv";
            boolean overwrite = cfg.hasPath("longfile.overwrite") && cfg.getBoolean("longfile.overwrite");

            // Skapa writer via dcsim-adaptern och skriv RUN-meta (t0=0.0, stage="NET")
            WRITER = LongFileWriter.forDcsim(csvPath, overwrite, project, scenario, hash, 0.0, "NET");

            System.out.println("[LongFiles] LongFileWriter ready at " + csvPath
                    + " project=" + project + " scenario=" + scenario + " hash=" + hash
                    + " overwrite=" + overwrite);
        }
    }

    /** Hämta den delade longtable-skrivaren (efter bootstrap). */
    public static LongFileWriter writer() {
        LongFileWriter w = WRITER;
        if (w == null) {
            throw new IllegalStateException("LongFiles not bootstrapped. Call LongFiles.bootstrapFromConfig() early in DcSimApp.");
        }
        return w;
    }

    /** Stäng snyggt (valfritt vid shutdown). */
    public static void closeQuietly() {
        LongFileWriter w = WRITER;
        if (w != null) {
            try { w.close(); } catch (Exception ignore) {}
        }
    }
}
