package org.dcsim.export;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * LongCsv – central helper för longtable-loggning via LongTableWriter.
 *
 * Krav i din LongTableWriter:
 *   new LongTableWriter(String path, boolean overwrite, String project, String scenario, String baseHash)
 *   void signalRow(Double time_s, String objectType, String objectId, String signal,
 *                  Double value, String unit, String stage, Integer iter, String note)
 *
 * Den här klassen är fristående (rör inte DcSimApp/aktörer).
 * Använd: LongCsv.signal(...), LongCsv.emitMetaOnce(t0).
 */
public final class LongCsv {
    private static final Object LOCK = new Object();
    private static volatile LongTableWriter WRITER = null;
    private static volatile boolean META_WRITTEN = false;

    // Kan sättas via -D system properties eller programmässigt via configure(...)
    private static volatile String projectName  = System.getProperty("dcsim.project",  "dc-simulator");
    private static volatile String scenarioName = System.getProperty("dcsim.scenario", "3subs1train");
    private static volatile String baseHash     = System.getProperty("dcsim.hash",     "8110f7deeaa232a8602fcccbd55f512cebdfc7af");
    private static volatile String longPath = System.getProperty("dcsim.longtable", "longtable.csv");
    private static volatile boolean overwrite   = true;

    private LongCsv() {}

    /** Valfri konfiguration i kod (annars använd -Dsystemproperties). */
    public static void configure(String project, String scenario, String hash, String csvPath, Boolean overwriteFile) {
        if (project != null && !project.isBlank())   projectName  = project;
        if (scenario != null && !scenario.isBlank()) scenarioName = scenario;
        if (hash != null && !hash.isBlank())         baseHash     = hash;
        if (csvPath != null && !csvPath.isBlank())   longPath     = csvPath;
        if (overwriteFile != null)                   overwrite    = overwriteFile.booleanValue();
    }


    private static String resolveLongPathDeterministically(String maybeRelativePath) {
        if (maybeRelativePath == null || maybeRelativePath.isBlank()) return "longtable.csv";

        Path raw = Paths.get(maybeRelativePath);
        if (raw.isAbsolute()) return raw.toString();

        // Preferred key first
        String resultsRootStr = System.getProperty("dcsim.paths.resultsDir");
        if (resultsRootStr == null || resultsRootStr.isBlank()) {
            resultsRootStr = System.getProperty("dcsim.resultsDir");
        }

        if (resultsRootStr == null || resultsRootStr.isBlank()) {
            throw new IllegalStateException(
                    "[LongCsv] dcsim.longtable is relative (" + raw + ") but no results root configured. " +
                            "Set -Ddcsim.paths.resultsDir=<ABSOLUTE> (preferred) or -Ddcsim.resultsDir=<ABSOLUTE>.");
        }

        Path resultsRoot = Paths.get(resultsRootStr);
        if (!resultsRoot.isAbsolute()) {
            throw new IllegalStateException("[LongCsv] results root must be ABSOLUTE. Got: " + resultsRoot);
        }

        return resultsRoot
                .resolve(projectName)
                .resolve(scenarioName)
                .resolve(raw)
                .normalize()
                .toString();
    }

    /** Lazy-init LongTableWriter med korrekt konstruktor. */
    public static LongTableWriter get() {
        LongTableWriter w = WRITER;
        if (w != null) return w;
        synchronized (LOCK) {
            if (WRITER == null) {
                try {
                    String resolved = resolveLongPathDeterministically(longPath);
                    longPath = resolved;
                    WRITER = new LongTableWriter(longPath, overwrite, projectName, scenarioName, baseHash);
                    System.out.println("[LongCsv] LongTableWriter initialized: " + longPath
                            + "  project=" + projectName + " scenario=" + scenarioName + " hash=" + baseHash);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to init LongTableWriter at " + longPath, e);
                }
            }
            return WRITER;
        }
    }

    /** Skriv en rad till longtable. */
    public static void signal(double time_s, String objectType, String objectId,
                              String signal, double value, String unit,
                              String stage, Integer iter, String note) {
        LongTableWriter w = get();
        Objects.requireNonNull(w, "LongTableWriter not initialized");
        w.signalRow(time_s, objectType, objectId, signal, value, unit, stage, iter, note);
    }

    /** Skriv RUN-meta (project/scenario/hash_tag) exakt en gång (lägg texten i 'note'). */
    public static void emitMetaOnce(double t) {
        if (META_WRITTEN) return;
        synchronized (LOCK) {
            if (META_WRITTEN) return;
            LongTableWriter w = get();
            w.signalRow(t, "RUN", "meta", "project",   0.0, "", "NET", null, projectName);
            w.signalRow(t, "RUN", "meta", "scenario",  0.0, "", "NET", null, scenarioName);
            w.signalRow(t, "RUN", "meta", "hash_tag",  0.0, "", "NET", null, baseHash);
            META_WRITTEN = true;
            System.out.println("[LongCsv] RUN meta written once.");
        }
    }

    /** Valfritt: städa. */
    public static void closeQuietly() {
        LongTableWriter w = WRITER;
        if (w != null) {
            try { w.close(); } catch (Exception ignore) {}
        }
    }
}
