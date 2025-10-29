package org.dcsim.testing;

import org.dcsim.electric.GridModel;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.build.NetBuilder;
import org.dcsim.solver.impl.DcDebug;
import org.dcsim.solver.impl.DcLinearSolver;
import org.dcsim.math.Real;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;

public final class TestHarness {

    public enum Source { NetBuilder /*, NetFixtures*/ }
    public enum Solver { Linear, Iterative }
    public enum GraphMode { NONE, TOPOLOGY /*, WITH_RESULTS*/ }

    private final Source source;
    private final Solver solver;
    private final GraphMode graph;
    private final boolean verbose;

    private TestHarness(Source source, Solver solver, GraphMode graph, boolean verbose) {
        this.source = source;
        this.solver = solver;
        this.graph = graph;
        this.verbose = verbose;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Source source = Source.NetBuilder;
        private Solver solver = Solver.Linear;
        private GraphMode graph = GraphMode.NONE;
        private boolean verbose = false;

        public Builder source(Source s)   { this.source = Objects.requireNonNull(s); return this; }
        public Builder solver(Solver s)   { this.solver = Objects.requireNonNull(s); return this; }
        public Builder graph(GraphMode g) { this.graph = Objects.requireNonNull(g); return this; }
        public Builder verbose(boolean v) { this.verbose = v; return this; }

        public TestHarness build() { return new TestHarness(source, solver, graph, verbose); }
    }

    public double[] solve(GridModel<?> model, Path dotOutOrNull) {
        // 1) Build DcNet via production path (cast to satisfy GridModel<Real>)
        @SuppressWarnings("unchecked")
        GridModel<Real> gm = (GridModel<Real>) model;

        final DcNet net = switch (source) {
            case NetBuilder -> NetBuilder.makeNet(gm);
            // case NetFixtures -> NetFixturesAdapter.makeNet(gm);
        };

        // 2) Verbose toggles
        if (verbose) {
            DcDebug.setVerbose(true);
            org.dcsim.solver.build.NetBuilder.VERBOSE = true;
            DcLinearSolver.VERBOSE = true;
        }

        // 3) Optional graph export (reflection – no hard dep)
        if (graph == GraphMode.TOPOLOGY && dotOutOrNull != null) {
            tryWriteDotTopology(model, dotOutOrNull);
        }

        // 4) Solve
        return switch (solver) {
            case Linear -> DcLinearSolver.solveVoltages(net, null);
            case Iterative -> solveIterativeOrFallback(net);
        };
    }

    // ---- Iterative solver via reflection (fallback to linear if missing) ----
    private double[] solveIterativeOrFallback(DcNet net) {
        try {
            // Try common class & method names
            String[] classes = {
                    "org.dcsim.solver.impl.DcIterativeSolver",
                    "org.dcsim.solver.DcIterativeSolver"
            };
            for (String fqcn : classes) {
                try {
                    Class<?> cls = Class.forName(fqcn);
                    // Prefer a static solveVoltages(DcNet, Object) or (DcNet)
                    Method m;
                    try {
                        m = cls.getMethod("solveVoltages", net.getClass(), Object.class);
                        return (double[]) m.invoke(null, net, null);
                    } catch (NoSuchMethodException ignore) {
                        m = cls.getMethod("solveVoltages", net.getClass());
                        return (double[]) m.invoke(null, net);
                    }
                } catch (ClassNotFoundException e) {
                    // try next
                }
            }
            DcDebug.log("[TestHarness] Iterative solver not found. Falling back to Linear.");
        } catch (Throwable t) {
            DcDebug.log("[TestHarness] Iterative solve failed (%s). Falling back to Linear.", t.getClass().getSimpleName());
        }
        return DcLinearSolver.solveVoltages(net, null);
    }

    // ---- Graph export via reflection (supports 2-arg or 3-arg signature) ----
    private static void tryWriteDotTopology(GridModel<?> model, Path out) {
        String[] fqcns = {
                "org.dcsim.testing.GraphExport",
                "org.dcsim.utils.GraphExport",
                "GraphExport"
        };
        for (String fqcn : fqcns) {
            try {
                Class<?> cls = Class.forName(fqcn);
                try {
                    // 3-args: (GridModel, Path, String)
                    var m3 = cls.getMethod("writeDotTopology", model.getClass(), Path.class, String.class);
                    m3.invoke(null, model, out, "Topology");
                    return;
                } catch (NoSuchMethodException ignore) {
                    // 2-args: (GridModel, Path)
                    var m2 = cls.getMethod("writeDotTopology", model.getClass(), Path.class);
                    m2.invoke(null, model, out);
                    return;
                }
            } catch (ClassNotFoundException e) {
                // next
            } catch (Throwable t) {
                // present but failed – ignore in tests
                return;
            }
        }
    }

    /** Convenience: dot -> png via Graphviz 'dot' if available. */
    public static boolean dotToPng(Path dotFile, Path pngFile) {
        try {
            Process p = new ProcessBuilder("dot", "-Tpng", dotFile.toString(), "-o", pngFile.toString())
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
