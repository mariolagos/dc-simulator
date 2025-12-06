package org.dcsim.electric;


import org.dcsim.export.LongTableWriter;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.impl.DcIterativeSolver;

import java.util.*;

import static org.dcsim.solver.build.NetBuilder.makeNet;

/**
 * DcIterativeAdapterSolver
 * ------------------------
 * Robust, kompilerande adapter som:
 * - Tar emot TrainAnchors (helst Map.Entry<trainId, TrainAnchorComponent>) via ElectricSolver.setTrainAnchors(...)
 * - Applicerar begärd effekt till TrainLoad före lösning (med singel/singel-fallback)
 * - Bygger DcNet via din NetBuilder utan reflection (hanterar båda signaturerna med NoSuchMethodError)
 * - Anropar DcIterativeSolver.solve(DcNet) och packar resultatet i GridResult(null, V)
 */
public final class DcIterativeAdapterSolver implements ElectricSolver {
    private static boolean finite(double x) { return !Double.isNaN(x) && !Double.isInfinite(x); }


    private static final boolean VERBOSE_ALL = Boolean.getBoolean("dcsim.verbose.all");

    // Senaste begärda effekt per tåg-id (W)
    private final Map<String, Double> lastRequestedPowerW = new HashMap<>();

    @Override
    public GridResult solve(GridModel model, double timeSec, int timestep) {
        // 1) Applicera senaste anchor-requests på TrainLoad
        applyAnchorsToTrainLoads(model);

        // 2) Bygg nät via din NetBuilder (utan reflection)
        final DcNet net = buildNetSafe(model, timeSec, timestep);

        // 3) Kör iterativa lösaren (din version tar endast DcNet)
        DcIterativeSolver solver = new DcIterativeSolver();
        solver.setSimTimeSec(timeSec);
        RealVector V = solver.solve(net);

        // Bygg ett "fullt" GridResult istället för bara (null, V)
        GridResult res = new GridResult(null, V);

        // 1) Fyll nodspänningar i map (om GridResult har set-metoder för det)
        for (Object nodeObj : model.getNodeIds()) {
            int nid = (nodeObj instanceof Integer)
                    ? (Integer) nodeObj
                    : Integer.parseInt(nodeObj.toString());

            if (nid < 0 || nid >= V.getDimension()) continue;

            double vNode = V.getEntry(nid);
            if (!Double.isFinite(vNode)) continue;

            // Anpassa till din API:
            res.setLatestNodeVoltage(nid, Real.fromDouble(vNode));
        }

        // 2) Fyll effekt för TrainLoad-enheter
        for (Object did : model.getDeviceIds()) {
            String devId = String.valueOf(did);
            Device<Real> dev = model.getDevice(devId);

            if (dev instanceof TrainLoad tl) {
                int nodeId = tl.getToNode();          // eller motsv.
                double vTrain = V.getEntry(nodeId);   // spänning vid tågnoden

                // Läs begärd nettoeffekt – med tecken:
                double pTotW = readRequestedPowerRobust(tl);
                // (denna pTotW kan vara negativ vid regen)

                // Om du vill: sanity-koll
                // if (!Double.isFinite(pTotW)) pTotW = 0.0;

                res.setLatestDevicePower(devId, Real.fromDouble(pTotW));
            }
            else if (dev instanceof Substation ss) {
                // klassiska substationformler:
                double E = ss.getEmf().asDouble();
                double R = ss.getInternalResistance().asDouble();
                if (R <= 0.0) continue;

                int nodeId = ss.getToNode();
                double vBus = V.getEntry(nodeId);

                double dV = E - vBus;
                double I  = dV / R;

                double pNetW  = vBus * I;        // effekt in i nätet (kan bli < 0 vid backfeed)
                double pLossW = (dV * dV) / R;   // intern förlust

                res.setLatestDevicePower(devId, Real.fromDouble(pNetW));
                // pLossW kan du antingen ta med i totals eller logga via PowerAccounting
            }
        }

        return res;
    }

    /**
     * ElectricSolver hook: tas emot från GridModelActor varje tick.
     * Skicka helst anchors.entrySet() så vi får id i key.
     */
    @Override
    public void setTrainAnchors(java.util.Collection<?> anchors, double dtSec) {
        lastRequestedPowerW.clear();
        if (anchors == null || anchors.isEmpty()) return;

        for (Object a : anchors) {
            String id = null;
            double pW = 0.0;

            if (a instanceof Map.Entry<?, ?> entry) {
                // Förväntat: id i key, komponent i value
                id = (entry.getKey() != null) ? String.valueOf(entry.getKey()) : null;
                Object comp = entry.getValue();
                pW = readRequestedPowerRobust(comp);
            } else {
                // Fallback: endast komponent — försök hitta id via getters/nested och läs power
                Object comp = a;
                pW = readRequestedPowerRobust(comp);
                id = extractTrainId(comp);
            }

            if (id != null) {
                lastRequestedPowerW.put(id, pW);
                if (VERBOSE_ALL) System.out.printf("[ADAPT] anchor id=%s req=%.1f W%n", id, pW);
            } else if (VERBOSE_ALL) {
                System.out.printf("[ADAPT] anchor without id: P=%.1f W%n", pW);
            }
        }

        if (VERBOSE_ALL) System.out.println("[ADAPT] anchors received: " + lastRequestedPowerW);
    }

    // ===== Hjälpare =====

    private void applyAnchorsToTrainLoads(GridModel model) {
        final boolean singleAnchorOnly = !lastRequestedPowerW.isEmpty() && lastRequestedPowerW.size() == 1;
        final Double singleAnchorPower = singleAnchorOnly ? lastRequestedPowerW.values().iterator().next() : null;

        for (Object o : model.getDevices()) {
            if (o instanceof TrainLoad tl) {
                Double p = lastRequestedPowerW.get(tl.getId());
                if (p == null && singleAnchorOnly) {
                    p = singleAnchorPower;
                }
                if (p != null) {
                    tl.setRequestedPower(Real.fromDouble(p));
                    if (VERBOSE_ALL)
                        System.out.printf("[ADAPT] applied requestedPower to TL %s : %.1f W%n", tl.getId(), p);
                } else {
                    // Sista fallback: hedra ev. redan satt requestedPower från aktorn
                    try {
                        Real rp = tl.getRequestedPower();
                        if (rp != null) {
                            tl.setRequestedPower(rp);
                            if (VERBOSE_ALL) System.out.printf("[ADAPT] fallback requestedPower from TL %s : %.1f W%n",
                                    tl.getId(), rp.asDouble());
                        }
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
    }

    // Bygg DcNet via din NetBuilder utan reflection, enligt signaturen:
// public static DcNet makeNet(GridModel<Real> model)
    @SuppressWarnings("unchecked")
    private DcNet buildNetSafe(GridModel model, double timeSec, int timestep) {
        // Om din NetBuilder ligger i ett annat paket, ändra FQCN nedan.
        return makeNet((GridModel<Real>) model);
    }

    /**
     * Läs total requested power om den finns; annars summera mot/brk/aux.
     */
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
            double b = brk != null ? brk : 0.0; // regen kan vara negativ
            double a = aux != null ? aux : 0.0;
            pTot = m + b + a;

            if (VERBOSE_ALL) {
                System.out.printf("[ADAPT] comp-> mot=%.1f, brk=%.1f, aux=%.1f => P=%.1f W%n", m, b, a, pTot);
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
        } catch (Throwable t) { /* ignore */ }
        return null;
    }

    /**
     * Fallback om vi inte fick Map.Entry: försök hitta tåg-id via getters/nested getTrain().
     */
    private static String extractTrainId(Object a) {
        if (a == null) return null;

        String[] simple = {"getTrainId", "trainId", "getId", "id", "getName", "name"};
        for (String m : simple) {
            try {
                Object v = a.getClass().getMethod(m).invoke(a);
                if (v != null) return String.valueOf(v);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) { /* continue */ }
        }
        try {
            Object tr = a.getClass().getMethod("getTrain").invoke(a);
            if (tr != null) {
                for (String m : simple) {
                    try {
                        Object v = tr.getClass().getMethod(m).invoke(tr);
                        if (v != null) return String.valueOf(v);
                    } catch (NoSuchMethodException ignored) {
                    } catch (Throwable t) { /* continue */ }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) { /* ignore */ }

        return null;
    }

    @Override
    public void setTrainRequestedPower(Map<String, Double> requestedPowerW, double dtSec) {
        lastRequestedPowerW.clear();
        if (requestedPowerW != null) {
            lastRequestedPowerW.putAll(requestedPowerW);
        }
        if (VERBOSE_ALL) System.out.println("[ADAPT] direct requestedPowerW: " + lastRequestedPowerW);
    }

}
