package org.dcsim.electric;

import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.impl.DcIterativeSolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.dcsim.solver.build.NetBuilder.makeNet;

/**
 * Adapter between production GridModel and iterative solver.
 *
 * Model side:
 *   - node identity = String node_id
 *
 * Solver side:
 *   - node identity = compact int index
 *
 * This class performs the mapping:
 *   String node_id -> net.tryIdxOf(node_id) -> int index
 */
public final class DcIterativeAdapterSolver implements ElectricSolver {

    private static final boolean VERBOSE_ALL = Boolean.getBoolean("dcsim.verbose.all");

    private final Map<String, Double> lastRequestedPowerW = new HashMap<>();

    private static boolean finite(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x);
    }

    @Override
    public GridResult solve(GridModel model, double timeSec, int timestep) {

        // 1) Apply latest anchor requests to TrainLoad
        applyAnchorsToTrainLoads(model);

        // 2) Build compact solver net
        final DcNet net = buildNetSafe(model, timeSec, timestep);

        // ===== DEBUG =====
        if (VERBOSE_ALL) {
            System.out.println("=== [ADAPT-NODES] nodeIndex from DcNet ===");
            for (Object nodeObj : model.getNodeIds()) {
                String nodeId = String.valueOf(nodeObj);
                Integer idx = net.tryIdxOf(nodeId);
                System.out.printf("[ADAPT-NODE] nodeId=%s idx=%s%n",
                        nodeId, String.valueOf(idx));
            }
            System.out.println("=== [ADAPT-NODES] end ===");

            System.out.println("=== [ADAPT-TRAINS] TrainLoad attachment ===");
            for (Object o : model.getDevices()) {
                if (o instanceof TrainLoad tl) {
                    String nodeId = tl.getToNode();
                    Integer idx = net.tryIdxOf(nodeId);
                    System.out.printf("[ADAPT-TRAIN] id=%s nodeId=%s idx=%s%n",
                            tl.getId(), nodeId, String.valueOf(idx));
                }
            }
            System.out.println("=== [ADAPT-TRAINS] end ===");

            System.out.println("=== [ADAPT-LINES] from DcNet ===");
            int i = 0;
            for (var L : net.lines()) {
                System.out.printf("[ADAPT-LINE] #%d id=%s a=%d b=%d R=%.6f%n",
                        i++, L.id(), L.a(), L.b(), L.r_ohm());
            }
            System.out.println("=== [ADAPT-LINES] end ===");
        }
        // ===== END DEBUG =====

        // 3) Solve
        DcIterativeSolver solver = new DcIterativeSolver();
        solver.setSimTimeSec(timeSec);
        RealVector V = solver.solve(net);

        GridResult res = new GridResult(null, V);

        // 4) Populate node voltages in result (keyed by node_id)
        for (Object nodeObj : model.getNodeIds()) {
            String nodeId = String.valueOf(nodeObj);
            Integer idx = net.tryIdxOf(nodeId);
            if (idx == null) continue;
            if (idx < 0 || idx >= V.getDimension()) continue;

            double vNode = V.getEntry(idx);
            if (!finite(vNode)) continue;

            res.setLatestNodeVoltage(nodeId, Real.fromDouble(vNode));
        }

        // 5) Populate device powers
        for (Object did : model.getDeviceIds()) {
            String devId = String.valueOf(did);
            Device<Real> dev = model.getDevice(devId);

            if (dev instanceof TrainLoad tl) {
                String nodeId = tl.getToNode();
                Integer idx = net.tryIdxOf(nodeId);
                if (idx == null || idx < 0 || idx >= V.getDimension()) continue;

                double vTrain = V.getEntry(idx);
                if (!finite(vTrain)) continue;

                double pTotW = readRequestedPowerRobust(tl);
                res.setLatestDevicePower(devId, Real.fromDouble(pTotW));

            } else if (dev instanceof Substation ss) {
                double E = ss.getEmf().asDouble();
                double R = ss.getInternalResistance().asDouble();
                if (!(R > 0.0)) continue;

                String nodeId = ss.getToNode();
                Integer idx = net.tryIdxOf(nodeId);
                if (idx == null || idx < 0 || idx >= V.getDimension()) continue;

                double vBus = V.getEntry(idx);
                if (!finite(vBus)) continue;

                double dV = E - vBus;
                double I = dV / R;

                double pNetW = vBus * I;
                double pLossW = (dV * dV) / R;

                res.setLatestDevicePower(devId, Real.fromDouble(pNetW));

                if (VERBOSE_ALL) {
                    System.out.printf("[ADAPT-SS] id=%s node=%s idx=%d vBus=%.3f E=%.3f R=%.6f Pnet=%.3f Ploss=%.3f%n",
                            devId, nodeId, idx, vBus, E, R, pNetW, pLossW);
                }
            }
        }

        return res;
    }

    @Override
    public void setTrainAnchors(Collection<?> anchors, double dtSec) {
        lastRequestedPowerW.clear();
        if (anchors == null || anchors.isEmpty()) return;

        for (Object a : anchors) {
            String id = null;
            double pW = 0.0;

            if (a instanceof Map.Entry<?, ?> entry) {
                id = (entry.getKey() != null) ? String.valueOf(entry.getKey()) : null;
                Object comp = entry.getValue();
                pW = readRequestedPowerRobust(comp);
            } else {
                Object comp = a;
                pW = readRequestedPowerRobust(comp);
                id = extractTrainId(comp);
            }

            if (id != null) {
                lastRequestedPowerW.put(id, pW);
                if (VERBOSE_ALL) {
                    System.out.printf("[ADAPT] anchor id=%s req=%.1f W%n", id, pW);
                }
            } else if (VERBOSE_ALL) {
                System.out.printf("[ADAPT] anchor without id: P=%.1f W%n", pW);
            }
        }

        if (VERBOSE_ALL) {
            System.out.println("[ADAPT] anchors received: " + lastRequestedPowerW);
        }
    }

    @Override
    public void setTrainRequestedPower(Map<String, Double> requestedPowerW, double dtSec) {
        lastRequestedPowerW.clear();
        if (requestedPowerW != null) {
            lastRequestedPowerW.putAll(requestedPowerW);
        }

        System.out.println(
                "[ADAPT-REQ] tick.dt=" + dtSec
                        + " reqW=" + lastRequestedPowerW
        );
    }

    private void applyAnchorsToTrainLoads(GridModel model) {
        final boolean singleAnchorOnly =
                !lastRequestedPowerW.isEmpty() && lastRequestedPowerW.size() == 1;

        final Double singleAnchorPower =
                singleAnchorOnly ? lastRequestedPowerW.values().iterator().next() : null;

        for (Object o : model.getDevices()) {
            if (o instanceof TrainLoad tl) {
                Double p = lastRequestedPowerW.get(tl.getId());
                if (p == null && singleAnchorOnly) {
                    p = singleAnchorPower;
                }

                if (p != null) {
                    tl.setRequestedPower(Real.fromDouble(p));
                    if (VERBOSE_ALL) {
                        System.out.printf("[ADAPT] applied requestedPower to TL %s : %.1f W%n",
                                tl.getId(), p);
                    }
                } else {
                    try {
                        Real rp = tl.getRequestedPower();
                        if (rp != null) {
                            tl.setRequestedPower(rp);
                            if (VERBOSE_ALL) {
                                System.out.printf("[ADAPT] fallback requestedPower from TL %s : %.1f W%n",
                                        tl.getId(), rp.asDouble());
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private DcNet buildNetSafe(GridModel model, double timeSec, int timestep) {
        return makeNet((GridModel<Real>) model);
    }

    private static double readRequestedPowerRobust(Object comp) {
        if (comp == null) return 0.0;

        Double pTot = firstNonNull(comp,
                "getRequestedTotalPowerW", "getRequestedTotalPower",
                "getTotalPowerW", "getTotalPower",
                "getPowerW", "getPower"
        );

        if (pTot == null || Math.abs(pTot) < 1e-12) {
            Double mot = firstNonNull(comp,
                    "getRequestedMotoringPowerW", "getRequestedMotoringPower",
                    "getMotoringPowerW", "getMotoringPower"
            );
            Double brk = firstNonNull(comp,
                    "getRequestedBrakingPowerW", "getRequestedBrakingPower",
                    "getBrakingPowerW", "getBrakingPower"
            );
            Double aux = firstNonNull(comp,
                    "getRequestedAuxiliaryPowerW", "getRequestedAuxiliaryPower",
                    "getAuxiliaryPowerW", "getAuxiliaryPower", "getAuxPowerW", "getAuxPower"
            );

            double m = mot != null ? mot : 0.0;
            double b = brk != null ? brk : 0.0;
            double a = aux != null ? aux : 0.0;
            pTot = m + b + a;

            if (VERBOSE_ALL) {
                System.out.printf("[ADAPT] comp-> mot=%.1f, brk=%.1f, aux=%.1f => P=%.1f W%n",
                        m, b, a, pTot);
            }
        }

        return (pTot != null) ? pTot : 0.0;
    }

    private static Double firstNonNull(Object o, String... methods) {
        for (String m : methods) {
            Double d = callRealOrNumber(o, m);
            if (d != null) return d;
        }
        return null;
    }

    private static Double callRealOrNumber(Object o, String method) {
        try {
            Object v = o.getClass().getMethod(method).invoke(o);
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v instanceof Real) return ((Real) v).asDouble();
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static String extractTrainId(Object a) {
        if (a == null) return null;

        String[] simple = {"getTrainId", "trainId", "getId", "id", "getName", "name"};

        for (String m : simple) {
            try {
                Object v = a.getClass().getMethod(m).invoke(a);
                if (v != null) return String.valueOf(v);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                // continue
            }
        }

        try {
            Object tr = a.getClass().getMethod("getTrain").invoke(a);
            if (tr != null) {
                for (String m : simple) {
                    try {
                        Object v = tr.getClass().getMethod(m).invoke(tr);
                        if (v != null) return String.valueOf(v);
                    } catch (NoSuchMethodException ignored) {
                    } catch (Throwable t) {
                        // continue
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            // ignore
        }

        return null;
    }
}