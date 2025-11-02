package org.dcsim.power;

import java.util.*;
import java.util.regex.*;
import org.dcsim.PowerPoint;
import org.dcsim.math.Real;

/**
 * Minimal TablePowerProfile
 * - expects List<PowerPoint> med (helst) följande fält (eller alias):
 *   time [s]           -> time / timeSec / time_s / seconds / t
 *   bisPosition [km,m] -> bisPosition / position / positionMeters
 *   speed [m/s]        -> speed / speed_mps / velocity / v
 *   primaryMotoringPower [kW]        -> primaryMotoringPower / motoringKW / motoring
 *   primaryMotorBrakingPower [kW]    -> primaryMotorBrakingPower / brakingKW / braking
 * - position tolkas i meter; bisPosition km+m accepteras med punkt- eller kommadecimaler
 * - hastighet härleds från position om den saknas
 * - power i W (kW*1000): pW = motW - brkW (netto)
 */
public final class TablePowerProfile implements PowerProfile {
    private final double[] tSec; // s
    private final double[] xM;   // m
    private final double[] vMS;  // m/s
    private final double[] pW;   // W (+mot, -brake)

    public TablePowerProfile(List<PowerPoint> src) {
        Objects.requireNonNull(src, "src");
        int n = src.size();
        if (n == 0) {
            this.tSec = new double[0];
            this.xM   = new double[0];
            this.vMS  = new double[0];
            this.pW   = new double[0];
            return;
        }

        double[] T = new double[n];
        double[] X = new double[n];
        double[] V = new double[n];
        double[] P = new double[n];

        for (int i = 0; i < n; i++) {
            PowerPoint pt = src.get(i);

            // --- time [s] ---
            Double tt = getNum(pt,
                    "time", "timeSec", "time_s", "seconds", "t", "Time", "Time_s");
            T[i] = (tt != null) ? tt : ((i == 0) ? 0.0 : T[i-1]);

            // --- power (kW -> W) ---
            Double motKW = getNum(pt,
                    "primaryMotoringPower", "primaryMotoringPower_kW", "primaryMotoringPower [kW]",
                    "motoringKW", "motoring");
            Double brkKW = getNum(pt,
                    "primaryMotorBrakingPower", "primaryMotorBrakingPower_kW", "primaryMotorBrakingPower [kW]",
                    "brakingKW", "braking");
            double motW = (motKW != null ? motKW*1000.0 : 0.0);
            double brkW = (brkKW != null ? brkKW*1000.0 : 0.0);
            P[i] = motW - brkW; // netto

            // --- speed [m/s] ---
            Double vv = getNum(pt,
                    "speed", "speed_mps", "speed [m/s]", "velocity", "v");
            V[i] = (vv != null) ? vv : Double.NaN;

            // --- position [m] ---
            Double xm = getNum(pt, "position", "positionMeters", "x", "s", "pos");
            if (xm == null) {
                String bis = getStr(pt, "bisPosition", "bisPosition [km,m]", "bis", "km,m");
                xm = parseBisKmM(bis);
            }
            X[i] = (xm != null) ? xm : ((i == 0) ? 0.0 : X[i-1]);
        }

        // Sortera på tid (om rådata inte är strikt växande)
        int[] order = argsort(T);
        this.tSec = new double[n];
        this.xM   = new double[n];
        this.vMS  = new double[n];
        this.pW   = new double[n];
        for (int i = 0; i < n; i++) {
            int k = order[i];
            tSec[i] = T[k];
            xM[i]   = X[k];
            vMS[i]  = V[k];
            pW[i]   = P[k];
        }

        // Härled speed från position om speed saknas
        deriveSpeedFromPosition();

        // Enkla skrubbningar (inga NaN/Inf)
        scrub(tSec, 0.0);
        scrub(xM,   (xM.length > 0 ? xM[0] : 0.0));
        scrub(vMS,  0.0);
        scrub(pW,   0.0);
    }

    // ----- Public API -----
    public OptionalDouble getSpeedAtTime(double t)    { return OptionalDouble.of(interp(tSec, vMS, t)); }
    public OptionalDouble getPositionAtTime(double t) { return OptionalDouble.of(interp(tSec, xM,  t)); }
    public Real getPowerAtTime(double t)    { return Real.fromDouble(interp(tSec, pW,  t)); }

    public double[] timesSec()    { return tSec.clone(); }
    public double[] positionsM()  { return xM.clone(); }
    public double[] speedsMps()   { return vMS.clone(); }
    public double[] powersW()     { return pW.clone(); }

    // ----- Internals -----

    private void deriveSpeedFromPosition() {
        final int n = tSec.length;
        if (n == 0) return;
        for (int i = 1; i < n; i++) {
            if (!finite(vMS[i])) {
                double dt = tSec[i] - tSec[i-1];
                if (dt > 1e-9) vMS[i] = (xM[i] - xM[i-1]) / dt;
            }
        }
        if (!finite(vMS[0])) vMS[0] = (n > 1 && finite(vMS[1])) ? vMS[1] : 0.0;
        for (int i = 1; i < n; i++) if (!finite(vMS[i])) vMS[i] = vMS[i-1];
    }

    private static double interp(double[] xs, double[] ys, double t) {
        int n = xs.length;
        if (n == 0) return 0.0;
        if (t <= xs[0])   return ys[0];
        if (t >= xs[n-1]) return ys[n-1];
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= t) lo = mid; else hi = mid;
        }
        double x0 = xs[lo], x1 = xs[hi];
        double y0 = ys[lo], y1 = ys[hi];
        if (!finite(y0) && finite(y1)) return y1;
        if (!finite(y1) && finite(y0)) return y0;
        if (!finite(y0) && !finite(y1)) return 0.0;
        double w = (x1 > x0) ? (t - x0) / (x1 - x0) : 0.0;
        return y0 + w * (y1 - y0);
    }

    private static void scrub(double[] a, double fallback) {
        for (int i = 0; i < a.length; i++) {
            if (!finite(a[i])) a[i] = (i > 0 ? a[i-1] : fallback);
        }
    }

    private static boolean finite(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x);
    }

    private static int[] argsort(double[] v) {
        Integer[] idx = new Integer[v.length];
        for (int i = 0; i < v.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> v[i]));
        int[] out = new int[v.length];
        for (int i = 0; i < v.length; i++) out[i] = idx[i];
        return out;
    }

    // ----- Very small reflection helpers -----

    // Försök läsa numeriskt fält via få rimliga alias (metod eller fältnamn).
    private static Double getNum(Object obj, String... names) {
        for (String n : names) {
            // field
            try {
                var f = obj.getClass().getField(n);
                Object v = f.get(obj);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (NoSuchFieldException ignore) { } catch (Throwable ignore) { }
            // getter
            try {
                String g = "get" + n.substring(0,1).toUpperCase(Locale.ROOT) + n.substring(1);
                var m = obj.getClass().getMethod(g);
                Object v = m.invoke(obj);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (NoSuchMethodException ignore) { } catch (Throwable ignore) { }
        }
        return null;
    }

    // Försök läsa strängfält via några alias.
    private static String getStr(Object obj, String... names) {
        for (String n : names) {
            // field
            try {
                var f = obj.getClass().getField(n);
                Object v = f.get(obj);
                if (v instanceof String) return (String) v;
            } catch (NoSuchFieldException ignore) { } catch (Throwable ignore) { }
            // getter
            try {
                String g = "get" + n.substring(0,1).toUpperCase(Locale.ROOT) + n.substring(1);
                var m = obj.getClass().getMethod(g);
                Object v = m.invoke(obj);
                if (v instanceof String) return (String) v;
            } catch (NoSuchMethodException ignore) { } catch (Throwable ignore) { }
        }
        return null;
    }

    // Tålig tolkning av "bisPosition [km,m]" → meter.
    // Accepterar "12+345", "12,345", "12 345", "12km+345m", och hanterar decimal-komma/punkt.
    private static Double parseBisKmM(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // Plocka de första två numeriska token, tillåt , eller . som decimaltecken.
        Matcher m = Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)").matcher(t);
        List<String> nums = new ArrayList<>();
        while (m.find()) {
            nums.add(m.group(1));
            if (nums.size() == 2) break;
        }
        if (nums.size() >= 2) {
            try {
                double km = Double.parseDouble(nums.get(0).replace(',', '.'));
                double mtr= Double.parseDouble(nums.get(1).replace(',', '.'));
                // trunkera meter-delen om du vill undvika decimaler:
                mtr = Math.floor(mtr);
                return km * 1000.0 + mtr;
            } catch (NumberFormatException ignore) {}
        }
        return null;
    }
}
