package org.dcsim.pivot;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "runPivot", mixinStandardHelpOptions = true,
        description = "A2 pivot tool – scenario-aware, with config/CLI overrides")
public final class PivotApp implements Runnable {
    @Option(names = "--project")
    String project;
    @Option(names = "--scenario")
    String scenario;
    @Option(names = "--hash")
    String hash;
    @Option(names = "--excel", description = "also create pivots.xlsx placeholder")
    boolean excel;
    @Option(names = "--root", description = "project root (default from application.conf)")
    Path root;
    @Option(names = "--verbose", description = "verbose logging")
    boolean verbose;

    @Option(names="--config", description="Path to application.conf") Path configPath;

    @Override
    public void run() {
        try {
            final com.typesafe.config.Config conf =
                    (configPath != null)
                            ? com.typesafe.config.ConfigFactory.parseFile(configPath.toFile())
                            .withFallback(com.typesafe.config.ConfigFactory.load())
                            .resolve()
                            : com.typesafe.config.ConfigFactory.load().resolve();

            // CLI / conf – men kräv INTE project/scenario om dcsim.paths.longtable finns
            String cfgProject  = (project  != null) ? project  : (conf.hasPath("dcsim.run.project")  ? conf.getString("dcsim.run.project")  : null);
            String cfgScenario = (scenario != null) ? scenario : (conf.hasPath("dcsim.run.scenario") ? conf.getString("dcsim.run.scenario") : null);
            if ("*".equals(hash)) hash = null; // <-- wildcard => inget hashfilter

            System.out.println("[Pivot] sys.config.file=" + System.getProperty("config.file"));
            System.out.println("[Pivot] conf has longtable? " + conf.hasPath("dcsim.paths.longtable"));

            Path longtable;
            if (conf.hasPath("dcsim.paths.longtable")) {
                longtable = Paths.get(conf.getString("dcsim.paths.longtable"));
            } else if (cfgProject != null && cfgScenario != null) {
                Path cfgRoot = (root != null) ? root : ConfigUtil.resolveRoot(conf);
                longtable = cfgRoot.resolve(cfgProject).resolve(cfgScenario).resolve("longtable.csv");
            } else {
                throw new IllegalStateException(
                        "Provide either dcsim.paths.longtable in application.conf OR both --project and --scenario (or set dcsim.run.project / dcsim.run.scenario)."
                );
            }

            Path outDir = conf.hasPath("dcsim.paths.pivots_dir")
                    ? Paths.get(conf.getString("dcsim.paths.pivots_dir"))
                    : longtable.getParent().resolve("pivots");
            Files.createDirectories(outDir);

            Path stationsCsv = outDir.resolve("stations.csv");
            Path trainsCsv   = outDir.resolve("trains.csv");
            Path linesCsv    = outDir.resolve("lines.csv");
            Path energyCsv   = outDir.resolve("energy.csv");
            Path xlsx        = outDir.resolve("pivots.xlsx");

            if (verbose) {
                System.out.printf("[Pivot] in=%s outDir=%s project=%s scenario=%s hash=%s excel=%s%n",
                        longtable.toAbsolutePath(), outDir.toAbsolutePath(),
                        (cfgProject == null ? "*" : cfgProject),
                        (cfgScenario == null ? "*" : cfgScenario),
                        (hash == null ? "*" : hash), excel);
            }
            if (!Files.exists(longtable)) {
                System.err.println("[Pivot] longtable.csv not found: " + longtable.toAbsolutePath());
                System.exit(2);
            }

            try (LongtableReader r = new LongtableReader(longtable, conf)) {
                StationsAggregator stations = new StationsAggregator(stationsCsv);
                TrainsAggregator   trains   = new TrainsAggregator(trainsCsv);
                LinesAggregator    lines    = new LinesAggregator();
                EnergyAggregator   energy   = new EnergyAggregator();

                long nNode = 0, nLine = 0, nTrain = 0;

                // <-- Nytt: håll aktuell RUN-metadata för rader som saknar project/scenario i CSV
                String ctxProject  = cfgProject;
                String ctxScenario = cfgScenario;
                String ctxHash     = hash;

                if (verbose) System.setProperty("pivot.verbose", "true");

                for (Record rec : r) {
                    // Om det är RUN-rad: uppdatera context och hoppa över rad i pivots
                    if (rec.kind == Record.Kind.RUN) {
                        if (rec.project  != null && !rec.project.isEmpty())   ctxProject  = rec.project;
                        if (rec.scenario != null && !rec.scenario.isEmpty())  ctxScenario = rec.scenario;
                        if (rec.hash_tag != null && !rec.hash_tag.isEmpty())  ctxHash     = rec.hash_tag;
                        continue;
                    }

                    // Fyll på meta från senaste RUN om saknas i raden
                    if (rec.project  == null) rec.project  = ctxProject;
                    if (rec.scenario == null) rec.scenario = ctxScenario;
                    if (rec.hash_tag == null) rec.hash_tag = ctxHash;

                    // Filtrera på NORMALISERADE värden (cfgProject/cfgScenario/hash)
                    // TEMP: ignorera filter helt för felsökning
                    // if (!Filter.match(...)) continue;

                    switch (rec.kind) {
                        case NODE -> { stations.accept(rec); energy.acceptNode(rec); nNode++; }
                        case TRAIN -> { trains.accept(rec);  energy.acceptTrain(rec); nTrain++; }
                        case LINE  -> { lines.accept(rec);   nLine++; }
                        default    -> { /* UNKNOWN -> ignoreras */ }
                    }
                }

                stations.close();
                trains.close();
                lines.write(linesCsv);
                energy.write(energyCsv, lines);
                if (excel) ExcelExporter.write(xlsx);

                if (verbose) {
                    System.out.printf("[Pivot] rows: NODE=%d LINE=%d TRAIN=%d%n", nNode, nLine, nTrain);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {

        new CommandLine(new PivotApp()).execute(args);
    }

    /**
     * Simple filter utility as inner class to keep patch compact.
     */
    static final class Filter {
        static boolean match(Record r, String project, String scenario, String hash) {
            boolean ok = true;
            if (r.project != null) ok &= r.project.equals(project);
            if (r.scenario != null) ok &= r.scenario.equals(scenario);
            if (hash != null && r.hash_tag != null) ok &= r.hash_tag.equals(hash);
            return ok;
        }
    }
}
