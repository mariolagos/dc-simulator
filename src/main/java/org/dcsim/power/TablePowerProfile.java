package org.dcsim.power;

import org.dcsim.PowerPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tabellifierar en lista av PowerPoint till kolumnserier.
 * Gör ingen omtolkning – läser bara fält från PowerPoint:
 *   T_s = time(), X_m = positionM(), V_mps = speedMS(), P_W = power(), U_V = voltage(), I_A = current()
 * Saknade värden lämnas som NaN (enligt PowerPoint).
 */
public final class TablePowerProfile {

    /** Tid [s] */
    public final double[] T_s;
    /** Position [m] (NaN om okänd) */
    public final double[] X_m;
    /** Hastighet [m/s] (NaN om okänd) */
    public final double[] V_mps;
    /** Effekt [W] */
    public final double[] P_W;
    /** Spänning [V] (NaN om okänd) */
    public final double[] U_V;
    /** Ström [A] (NaN om okänd) */
    public final double[] I_A;

    /**
     * Bygger serier från en lista av PowerPoint.
     * Listan kopieras och sorteras på tid för determinism.
     */
    public TablePowerProfile(List<PowerPoint> in) {
        if (in == null || in.isEmpty()) {
            this.T_s  = new double[0];
            this.X_m  = new double[0];
            this.V_mps= new double[0];
            this.P_W  = new double[0];
            this.U_V  = new double[0];
            this.I_A  = new double[0];
            return;
        }

        // Kopiera + sortera på tid
        List<PowerPoint> pts = new ArrayList<>(in);
        pts.sort(Comparator.comparingDouble(PowerPoint::time));

        final int n = pts.size();
        double[] T = new double[n];
        double[] X = new double[n];
        double[] V = new double[n];
        double[] P = new double[n];
        double[] U = new double[n];
        double[] I = new double[n];

        for (int k = 0; k < n; k++) {
            PowerPoint p = pts.get(k);
            T[k] = p.time();
            P[k] = p.power();
            U[k] = p.voltage();   // kan vara NaN
            I[k] = p.current();   // kan vara NaN
            X[k] = p.positionM(); // NaN om okänd
            V[k] = p.speedMS();   // NaN om saknas
        }

        this.T_s   = T;
        this.X_m   = X;
        this.V_mps = V;
        this.P_W   = P;
        this.U_V   = U;
        this.I_A   = I;
    }

    /**
     * Alternativ konstruktor: fyll NaN i X/V med fallback (t.ex. 0.0) om du vill slippa NaN senare.
     */
    public TablePowerProfile(List<PowerPoint> in, boolean replaceNaN, double fallbackValueForXandV) {
        this(in);
        if (replaceNaN) {
            replaceNaNInPlace(this.X_m,   fallbackValueForXandV);
            replaceNaNInPlace(this.V_mps, fallbackValueForXandV);
            // Behåll U_V och I_A som NaN – ofta bättre i nätanalys
        }
    }

    private static void replaceNaNInPlace(double[] a, double withVal) {
        for (int i = 0; i < a.length; i++) {
            if (Double.isNaN(a[i])) a[i] = withVal;
        }
    }
}
