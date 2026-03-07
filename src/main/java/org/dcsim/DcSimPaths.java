package org.dcsim; // eller org.dcsim.paths / org.dcsim.app

import com.typesafe.config.Config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class DcSimPaths {

    public record OutputRoots(Path exportDir, Path resultsDir, Path effectiveDir, Path longtablePath) {
    }

    private DcSimPaths() {
    }

    /**
     * Resolve output locations deterministically, independent of working directory.
     *
     * Rules:
     * - If dcsim.exportInputs is present: exportDir = exportInputs (absolute or confDir-relative)
     * - Else: exportDir = export.root / project / scenario
     * - resultsDir = results.root / project / scenario (always)
     * - effectiveDir defaults to resultsDir
     * - longtablePath defaults to resultsDir/longtable.csv unless overridden
     */
    public static OutputRoots resolveOutputs(Config dcsim, Path confFile) {
        Objects.requireNonNull(dcsim, "dcsim");
        Objects.requireNonNull(confFile, "confFile");

        confFile = confFile.toAbsolutePath().normalize();

        Path confDir = confFile.getParent();
        if (confDir == null) confDir = Paths.get(".").toAbsolutePath().normalize();

        String project = dcsim.hasPath("project") ? dcsim.getString("project") : "no-project";
        String scenarioId = dcsim.hasPath("scenario") ? dcsim.getString("scenario") : "no-scenario";

        // results.root (required for deterministic behavior)
        Path resultsRoot = dcsim.hasPath("results.root")
                ? resolveMaybeRelativeToConfDir(Paths.get(dcsim.getString("results.root")), confDir)
                : confDir.resolve("output").resolve("results"); // safe default

        Path resultsDir = resultsRoot.resolve(project).resolve(scenarioId).normalize();

        // export directory: deterministic; configured via export.root (optional)
        Path exportRoot = dcsim.hasPath("export.root")
                ? resolveMaybeRelativeToConfDir(Paths.get(dcsim.getString("export.root")), confDir)
                : confDir.resolve("output").resolve("export"); // safe default

        Path exportDir = exportRoot.resolve(project).resolve(scenarioId).normalize();

        Path effectiveDir = resultsDir;

        Path longtablePath;
        if (dcsim.hasPath("longtable")) {
            // allow explicit path, but keep deterministic by resolving relative to resultsDir
            Path raw = Paths.get(dcsim.getString("longtable"));
            longtablePath = raw.isAbsolute() ? raw : resultsDir.resolve(raw).normalize();
        } else {
            longtablePath = resultsDir.resolve("longtable.csv");
        }

        return new OutputRoots(exportDir, resultsDir, effectiveDir, longtablePath);
    }

    private static Path resolveMaybeRelativeToConfDir(Path p, Path confDir) {
        return p.isAbsolute() ? p : confDir.resolve(p).normalize();
    }
}