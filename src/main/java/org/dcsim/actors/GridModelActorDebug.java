package org.dcsim.actors;

import java.util.Map;

/**
 * Drop-in helper to inject throttled debug prints and a safe SMOKE fallback (200 kW on T1 if missing/zero).
 * Usage (one-liner), right before calling the solver:
 *
 *   // example in GridModelActor, just before solver.setTrainRequestedPower(...):
 *   reqW_direct = org.dcsim.actors.GridModelActorDebug
 *       .smokeAndLog(reqW_direct, step, lastStep, timeSec, 1);
 *   this.solver.setTrainRequestedPower(reqW_direct, this.trainDtSec);
 *
 * If you don't have step/lastStep/timeSec in scope, pass 0.
 */
public final class GridModelActorDebug {

    private GridModelActorDebug() {}

    public static Map<String, Double> smokeAndLog(Map<String, Double> reqW_direct,
                                                  int step, int lastStep, double timeSec,
                                                  int verbosity) {
        if (reqW_direct == null) return null;

        // SMOKE: if T1 is missing or ~0 -> force 200 kW
        try {
            Double pv = reqW_direct.get("T1");
            if (pv == null || Math.abs(pv) < 1e-3) {
                reqW_direct.put("T1", 200_000.0); // 200 kW
                if (verbosity >= 1) {
                    System.out.println("[SMOKE] forcing T1 = 200 kW this tick");
                }
            }
        } catch (Throwable ignore) { /* no-op */ }

        // Throttled logging
        if (verbosity >= 1 && shouldPrintStep(step, lastStep)) {
            double totalReq = 0.0;
            for (Map.Entry<String, Double> e : reqW_direct.entrySet()) {
                totalReq += (e.getValue() != null ? e.getValue() : 0.0);
            }
            System.out.printf("[GMA] t=%.1fs step=%d/%d trains=%d reqW_sum=%.1f W%n",
                    timeSec, step, lastStep, reqW_direct.size(), totalReq);

            if (verbosity >= 2) {
                for (Map.Entry<String, Double> e : reqW_direct.entrySet()) {
                    System.out.printf("  [GMA-DETAIL] %s -> %.1f W%n",
                            e.getKey(), e.getValue() != null ? e.getValue() : 0.0);
                }
            }
        }

        return reqW_direct;
    }

    private static boolean shouldPrintStep(int step, int lastStep) {
        return step == 0 || step == lastStep || (step % 50 == 0);
    }
}
