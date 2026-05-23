package org.dcsim.opt;

import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.math.Real;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.*;
import org.supply.domain.Node;

import java.util.*;

/**
 * LP-baserad allokering av rekupererad effekt till konsumenter.
 * Minimerar sum(cost_ij * f_ij) s.t.
 *   - För varje källa i:  sum_j f_ij <= R_i
 *   - För varje sänka j:  sum_i f_ij <= D_j
 *   - f_ij >= 0
 *
 * cost_ij = elektrisk "närhet" (kortaste väg viktad med linjernas resistans).
 */
public final class RegenAllocator {

    public static final class Source {
        public final String id;      // t ex train-id
        public final int nodeId;     // nod där källan sitter
        public final double supplyKW; // tillgänglig regen (kW)
        public Source(String id, int nodeId, double supplyKW) {
            this.id = id; this.nodeId = nodeId; this.supplyKW = supplyKW;
        }
    }

    public static final class Sink {
        public final String id;      // t ex train-id (motoring) eller "LOAD:xyz"
        public final int nodeId;
        public final double demandKW; // hur mycket som kan tas emot (kW)
        public Sink(String id, int nodeId, double demandKW) {
            this.id = id; this.nodeId = nodeId; this.demandKW = demandKW;
        }
    }

    public static final class Result {
        /** Summa f_ij per källa i (kW) – hur mycket som faktiskt går till nätet från respektive källa */
        public final Map<String, Double> netBySourceKW = new LinkedHashMap<>();
        /** Alla flöden för debug: key "srcId->sinkId" */
        public final Map<String, Double> flowsKW = new LinkedHashMap<>();
        /** Objektivvärde (sum cost * flow) */
        public final double objective;
        Result(double objective) { this.objective = objective; }
    }

    /** Kör LP:t. Returnerar nollflöde om det saknas sänkor. */
    public static Result allocate(GridModel<Real> model,
                                  List<Source> sources,
                                  List<Sink> sinks) {

        Result empty = new Result(0.0);
        for (Source s : sources) empty.netBySourceKW.put(s.id, 0.0);

        if (sources.isEmpty() || sinks.isEmpty()) return empty;

        // 1) Bygg resistansgraf (nod -> [granne, R])
        Map<String, List<Edge>> graph = buildResistanceGraph(model);

        // 2) Kostnadsmatris (kortaste väg i R-summa)
        final int nS = sources.size();
        final int nT = sinks.size();
        double[][] cost = new double[nS][nT];
        for (int i = 0; i < nS; i++) {
            Map<String, Double> dist = dijkstra(graph, String.valueOf(sources.get(i).nodeId));
            for (int j = 0; j < nT; j++) {
                double d = dist.getOrDefault(sinks.get(j).nodeId, Double.POSITIVE_INFINITY);
                cost[i][j] = d;
            }
        }

        // 3) LP-variabler: ett f_ij för varje par med ändlig kostnad
        // Vi packar variabler i en lista för enkelhets skull
        final List<Var> vars = new ArrayList<>();
        for (int i = 0; i < nS; i++) {
            for (int j = 0; j < nT; j++) {
                if (Double.isFinite(cost[i][j])) {
                    vars.add(new Var(i, j, cost[i][j]));
                }
            }
        }
        if (vars.isEmpty()) return empty;

        // 4) Mål: minimera sum(cost_k * x_k)
        double[] c = new double[vars.size()];
        for (int k = 0; k < vars.size(); k++) c[k] = vars.get(k).cost;
        LinearObjectiveFunction obj = new LinearObjectiveFunction(c, 0.0);

        Collection<LinearConstraint> cons = new ArrayList<>();

        // 5) Källa: sum_j f_ij <= R_i
        for (int i = 0; i < nS; i++) {
            double[] a = new double[vars.size()];
            for (int k = 0; k < vars.size(); k++) if (vars.get(k).i == i) a[k] = 1.0;
            cons.add(new LinearConstraint(a, Relationship.LEQ, sources.get(i).supplyKW));
        }

        // 6) Sänka: sum_i f_ij <= D_j
        for (int j = 0; j < nT; j++) {
            double[] a = new double[vars.size()];
            for (int k = 0; k < vars.size(); k++) if (vars.get(k).j == j) a[k] = 1.0;
            cons.add(new LinearConstraint(a, Relationship.LEQ, sinks.get(j).demandKW));
        }

        // 7) Icke-negativitet – NonNegativeConstraint(true) räcker, men lägger också explicita
        //    (hjälper ibland numerik)
        for (int k = 0; k < vars.size(); k++) {
            double[] a = new double[vars.size()];
            a[k] = 1.0;
            cons.add(new LinearConstraint(a, Relationship.GEQ, 0.0));
        }

        PointValuePair sol;
        try {
            sol = new SimplexSolver().optimize(
                    new MaxIter(2000),
                    obj,
                    new LinearConstraintSet(cons),
                    GoalType.MINIMIZE,
                    new NonNegativeConstraint(true)
            );
        } catch (Exception e) {
            // Faller tillbaka: inget flöde
            return empty;
        }

        double[] x = sol.getPoint();
        Result out = new Result(sol.getValue());
        for (Source s : sources) out.netBySourceKW.put(s.id, 0.0);

        for (int k = 0; k < vars.size(); k++) {
            double f = x[k];
            if (f <= 1e-9) continue;
            Var v = vars.get(k);
            Source src = sources.get(v.i);
            Sink   snk = sinks.get(v.j);
            out.netBySourceKW.put(src.id, out.netBySourceKW.get(src.id) + f);
            out.flowsKW.put(src.id + "->" + snk.id, f);
        }
        return out;
    }

    // ---------- hjälpstrukturer ----------

    private static final class Var {
        final int i, j;
        final double cost;
        Var(int i, int j, double cost) { this.i = i; this.j = j; this.cost = cost; }
    }
    private static final class Edge {
        final String to; final double R;
        Edge(String to, double R) { this.to = to; this.R = R; }
    }

    private static Map<String, List<Edge>> buildResistanceGraph(GridModel<Real> model) {
        Map<String, List<Edge>> g = new HashMap<>();
        for (Object did : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(String.valueOf(did));
            if (d instanceof Line ln) {
                String u = ln.getFromNode();
                String v = ln.getToNode();
                double R = ln.getResistance().asDouble();
                g.computeIfAbsent(u, k -> new ArrayList<>()).add(new Edge(v, R));
                g.computeIfAbsent(v, k -> new ArrayList<>()).add(new Edge(u, R));
            }
        }
        // säkerställ att alla noder finns
        for (String n : model.getNodeIds()) {
            g.computeIfAbsent(n, k -> new ArrayList<>());
        }
        return g;
    }

    private static Map<String, Double> dijkstra(Map<String, List<Edge>> g, String src) {
        Map<String, Double> dist = new HashMap<>();
        for (String n : g.keySet()) {
            dist.put(n, Double.POSITIVE_INFINITY);
        }
        dist.put(src, 0.0);

        class NodeDist {
            final String v;
            final double d;

            NodeDist(String v, double d) {
                this.v = v;
                this.d = d;
            }
        }

        PriorityQueue<NodeDist> q =
                new PriorityQueue<>(Comparator.comparingDouble(n -> n.d));
        q.add(new NodeDist(src, 0.0));

        while (!q.isEmpty()) {
            NodeDist cur = q.poll();

            if (cur.d > dist.get(cur.v) + 1e-15) {
                continue;
            }

            for (Edge e : g.getOrDefault(cur.v, List.of())) {
                double nd = cur.d + e.R;
                if (nd + 1e-15 < dist.get(e.to)) {
                    dist.put(e.to, nd);
                    q.add(new NodeDist(e.to, nd));
                }
            }
        }

        return dist;
    }
}
