package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.TrainData;

import static org.dcsim.solver.impl.DcDebug.log;

public final class DcStamps {
    private DcStamps() {}

    /** Diodlik substation (Thevenin E,R -> Norton g,E) med backfeed-gating. */
    public static void stampSubstation(
            RealVector V, RealMatrix G, RealVector J,
            int a, int b, double E, double R,
            boolean allowBackfeed, double eps, double gLeakDiag
    ) {
        if (R <= 0.0) return;

        final double g   = 1.0 / R;
        final double Va  = V.getEntry(a);
        final double Vb  = V.getEntry(b);
        final double dV  = Va - Vb;
        final boolean active = allowBackfeed || (dV <= E + eps);

        if (active) {
            // ledningsförmåga mellan a och b
            G.addToEntry(a, a, +g);
            G.addToEntry(b, b, +g);
            G.addToEntry(a, b, -g);
            G.addToEntry(b, a, -g);
            // Norton-källa a->b
            final double Isrc = g * E;
            J.addToEntry(a, +Isrc);
            J.addToEntry(b, -Isrc);

            log("[SS a=%d b=%d] ACTIVE  dV=%.3fV  E=%.3fV  R=%.6fΩ  g=%.3e  Isrc=%.3fA  P≈%.1fW",
                    a, b, dV, E, R, g, Isrc, Isrc * dV);
        } else {
            // Spärr: ingen gren, bara minimal diagonal-läcka för numerik
            if (gLeakDiag > 0.0) {
                G.addToEntry(a, a, gLeakDiag);
                G.addToEntry(b, b, gLeakDiag);
            }
            log("[SS a=%d b=%d] BLOCKED dV=%.3fV  E=%.3fV  (backfeed=DISABLED) -> leak diag=%.3e",
                    a, b, dV, E, gLeakDiag);
        }
    }

    /**
     * Tåg med vmin/vmax/imax + per-island motor-enable.
     *  req_W > 0  ⇒ motor (absorberar från nätet)
     *  req_W < 0  ⇒ regen (matar till nätet)
     */
    public static void stampTrain(
            RealVector V, RealMatrix G, RealVector J,
            TrainData tr,
            double vminDefault,      // rekommenderat 0.0: använd endast tr.vmin_V()
            boolean motorEnabled
    ) {
        final int a = tr.a();
        final int b = tr.b();

        final double Va   = V.getEntry(a);
        final double Vb   = V.getEntry(b);
        final double dV   = Va - Vb;
        final double vabs = Math.abs(dV);



        final double req   = tr.req_W();              // + motor, - regen
        final double imax  = Math.max(0.0, tr.imax_A());
        final double vminT = tr.vmin_V();
        final double vmax  = Math.max(tr.vmax_V(), (vminT > 0 ? vminT : 0) + 1e-9);

        // Använd ENBART tågets vmin om >0; annars ingen throttle.
        final double vmin = (vminT > 0.0) ? vminT : Math.max(0.0, vminDefault);

        double effReq = 0.0;

        if (req > 0.0) {
            if (motorEnabled) {
                // Traction derating based on V_derate1/V_derate2
                double v1 = tr.vDerate1_V();
                double v2 = tr.vDerate2_V();

                double alpha;
                if (Double.isNaN(vabs) || vabs <= 0.0) {
                    // Ingen meningsfull spänning → ingen traction
                    alpha = 0.0;
                } else if (v2 <= v1 + 1e-9) {
                    // Degenerat fall: ingen derating konfigurerad → alltid full kraft
                    alpha = 1.0;
                } else if (vabs <= v1) {
                    alpha = 0.0;
                } else if (vabs >= v2) {
                    alpha = 1.0;
                } else {
                    alpha = (vabs - v1) / (v2 - v1);
                }

                effReq = req * alpha;  // 0..req
            } else {
                effReq = 0.0;
            }
        } else if (req < 0.0) {
            // Regen – oförändrad OVP-logik
            if (vabs >= vmax - 1e-12) {
                effReq = 0.0;         // regen stoppas över vmax
            } else {
                effReq = req;         // negativt
            }
        }

        if (Math.abs(effReq) < 1e-12 || vabs < 1e-12) {
            log("[TRAIN %s] req=%.1fW vabs=%.3fV vmin=%.1f vmax=%.1f motorEnabled=%s -> Iab=0.000A P=0.0W",
                    tr.id(), req, vabs, vmin, vmax, motorEnabled);
            return;
        }

        double I = effReq / vabs;
        if (Math.abs(I) > imax) {
            I = Math.copySign(imax, I);
        }

        // Motor (effReq>0): ström b->a  ⇒ J[b]+=I, J[a]-=I
        // Regen (effReq<0): ström a->b  ⇒ J[a]+=|I|, J[b]-=|I|
        if (I > 0) {
            J.addToEntry(b, +I);
            J.addToEntry(a, -I);
        } else {
            final double Imag = -I;
            J.addToEntry(a, +Imag);
            J.addToEntry(b, -Imag);
        }

        log("[TRAIN %s] req=%.1fW vabs=%.3fV vmin=%.1f vmax=%.1f motorEnabled=%s -> Iab=%.3fA P=%.1fW",
                tr.id(), req, vabs, vmin, vmax, motorEnabled, I, I * dV);
    }
}
