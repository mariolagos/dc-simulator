package org.dcsim;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable sample point for power/kinematics.
 * All energies/powers are SI: power [W], voltage [V], current [A], speed [m/s], position [m].
 *
 * Position can be represented in three equivalent ways:
 *  - positionString: legacy "km+m" string (e.g., "12+345")
 *  - bisKm / bisM: integer kilometers and meters (0..999) in BIS format
 *  - positionM: absolute meters (double)
 *
 * Speed is always m/s. Missing values are represented as Double.NaN.
 */
public final class PowerPoint {

    private final double timeS;     // seconds
    private final double powerW;    // W
    private final double voltageV;  // V
    private final double currentA;  // A

    // Kinematics (all optional; if unknown -> NaN / null)
    private final Double positionM;     // meters (nullable)
    private final Integer bisKm;        // km part (nullable)
    private final Integer bisM;         // m part (0..999), nullable
    private final String positionString;// original "km+m" or normalized, nullable
    private final Double speedMS;       // m/s (nullable)

    private PowerPoint(
            double timeS,
            double powerW,
            double voltageV,
            double currentA,
            Double positionM,
            Integer bisKm,
            Integer bisM,
            String positionString,
            Double speedMS
    ) {
        this.timeS = timeS;
        this.powerW = powerW;
        this.voltageV = voltageV;
        this.currentA = currentA;
        this.positionM = positionM;
        this.bisKm = bisKm;
        this.bisM = bisM;
        this.positionString = positionString;
        this.speedMS = speedMS;
    }

    /* ===================== FACTORIES ===================== */

    /** Minimal point (no kinematics). */
    public static PowerPoint of(double timeS, double powerW, double voltageV, double currentA) {
        return new PowerPoint(timeS, powerW, voltageV, currentA, null, null, null, null, null);
    }

    /** With numeric position [m] and speed [m/s]. */
    public static PowerPoint ofPositionM(
            double timeS, double powerW, double voltageV, double currentA,
            Double positionM, Double speedMS
    ) {
        return new PowerPoint(timeS, powerW, voltageV, currentA,
                positionM, null, null,
                (positionM != null && !positionM.isNaN())
                        ? bisFormatFromMeters(positionM) : null,
                speedMS);
    }

    /** With BIS km+m (integers) and speed [m/s]. */
    public static PowerPoint ofBis(
            double timeS, double powerW, double voltageV, double currentA,
            Integer bisKm, Integer bisM, Double speedMS
    ) {
        Double posM = (bisKm != null && bisM != null) ? (bisKm * 1000.0 + bisM) : null;
        return new PowerPoint(timeS, powerW, voltageV, currentA,
                posM, bisKm, bisM, bisFormat(bisKm, bisM), speedMS);
    }

    /** With legacy "km+m" string and speed [m/s]. */
    public static PowerPoint ofPositionString(
            double timeS, double powerW, double voltageV, double currentA,
            String positionString, Double speedMS
    ) {
        ParsedBis p = parseBis(positionString);
        Double posM = (p != null) ? (p.km * 1000.0 + p.m) : null;
        return new PowerPoint(timeS, powerW, voltageV, currentA,
                posM, (p != null ? p.km : null), (p != null ? p.m : null),
                normalizePosString(positionString, p), speedMS);
    }

    /* ===================== GETTERS (STABLE API) ===================== */

    public double time()    { return timeS; }
    public double power()   { return powerW; }
    public double voltage() { return voltageV; }
    public double current() { return currentA; }

    /** True if numeric position in meters is present. */
    public boolean hasPositionM() { return positionM != null; }
    /** Position in meters (NaN if missing). */
    public double positionM() { return positionM != null ? positionM : Double.NaN; }

    /** True if BIS km+m is present. */
    public boolean hasBis() { return bisKm != null && bisM != null; }
    /** BIS kilometers (or -1 if missing). */
    public int bisKm() { return bisKm != null ? bisKm : -1; }
    /** BIS meters (0..999) (or -1 if missing). */
    public int bisM() { return bisM != null ? bisM : -1; }

    /** Position as "km+m" if known; otherwise empty string. */
    public String positionString() { return positionString != null ? positionString : ""; }

    /** True if speed [m/s] is present. */
    public boolean hasSpeedMS() { return speedMS != null; }
    /** Speed [m/s] (NaN if missing). */
    public double speedMS() { return speedMS != null ? speedMS : Double.NaN; }

    /* ===================== LEGACY COMPAT (REMOVE LATER) ===================== */

    /** @deprecated Use positionString(), positionM(), bisKm()/bisM(). */
    @Deprecated public String position() { return positionString(); }

    /** @deprecated Immutable now. Left only to catch old calls early. */
    @Deprecated public void setSpeedMps(double v) {
        throw new UnsupportedOperationException("PowerPoint is immutable; set speed at construction.");
    }

    /* ===================== INTERNAL HELPERS ===================== */

    private static String bisFormat(Integer km, Integer m) {
        if (km == null || m == null) return "";
        return km + "+" + m;
    }

    private static String bisFormatFromMeters(double posM) {
        if (Double.isNaN(posM)) return "";
        int km = (int) Math.floor(posM / 1000.0);
        int m  = (int) Math.round(posM - km * 1000.0);
        return km + "+" + m;
    }

    private static String normalizePosString(String src, ParsedBis p) {
        if (p == null) return src == null ? "" : src.trim();
        return p.km + "+" + p.m;
    }

    /** Parsed BIS helper. */
    private static final class ParsedBis {
        final int km, m;
        ParsedBis(int km, int m) { this.km = km; this.m = m; }
    }

    /**
     * Parse "km+m" (e.g., "12+345"). Accepts whitespace; meters can be decimal - we truncate.
     */
    public static ParsedBis parseBis(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        String[] parts = t.split("\\+");
        if (parts.length != 2) return null;
        try {
            int km = Integer.parseInt(parts[0].trim());
            String mPart = parts[1].trim();
            // allow decimals, truncate toward zero
            int m = (int) Math.floor(Double.parseDouble(mPart));
            if (m < 0) m = 0;
            if (m > 999_999) m = 999_999; // generous guard
            return new ParsedBis(km, m);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override public String toString() {
        return String.format(Locale.ROOT,
                "PP{t=%.3fs, P=%.1fW, V=%.1fV, I=%.1fA, pos=%s (%.3fm), v=%.3fm/s}",
                timeS, powerW, voltageV, currentA, positionString(), positionM(), speedMS());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PowerPoint)) return false;
        PowerPoint that = (PowerPoint) o;
        return Double.compare(timeS, that.timeS) == 0
                && Double.compare(powerW, that.powerW) == 0
                && Double.compare(voltageV, that.voltageV) == 0
                && Double.compare(currentA, that.currentA) == 0
                && Objects.equals(positionM, that.positionM)
                && Objects.equals(bisKm, that.bisKm)
                && Objects.equals(bisM, that.bisM)
                && Objects.equals(positionString, that.positionString)
                && Objects.equals(speedMS, that.speedMS);
    }

    @Override public int hashCode() {
        return Objects.hash(timeS, powerW, voltageV, currentA, positionM, bisKm, bisM, positionString, speedMS);
    }
}
