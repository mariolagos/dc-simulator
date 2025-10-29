package org.dcsim.track;

/**
 * TrackPosition represents a coordinate inside one track section: s km+m.
 * - s: section id (non-negative integer)
 * - km: kilometer within section (non-negative integer)
 * - m: meters within kilometer (may be fractional internally; TRUNCATED on format)
 *
 * Sign conventions / rules:
 * - km >= 0, m >= 0. Normalization can roll m >= 1000 into km+1.
 * - Formatting truncates m to integer meters (no rounding).
 */
public final class TrackPosition {
    private final int section;
    private final int km;
    private final double m; // may contain decimals internally

    public TrackPosition(int section, int km, double m) {
        if (section < 0 || km < 0 || m < 0) {
            throw new IllegalArgumentException("TrackPosition values must be non-negative");
        }
        this.section = section;
        this.km = km;
        this.m = m;
    }

    public int section() { return section; }
    public int km() { return km; }
    public double meters() { return m; }

    /** Returns meters within the section (km*1000 + m). */
    public double toMetersInSection() { return km * 1000.0 + m; }

    /** Normalize so that m < 1000.0 by carrying overflows into km. */
    public TrackPosition normalized() {
        int extraKm = (int) Math.floor(m / 1000.0);
        double newM = m - extraKm * 1000.0;
        return new TrackPosition(section, km + extraKm, newM);
    }

    /** Formats as "s km+m" where m is TRUNCATED (no rounding). */
    public String format() {
        long truncM = (long) Math.floor(this.meters() % 1000.0);
        return section + " " + km + "+" + truncM;
    }

    /** Parse "s km+m" where s,km are integers; m may be decimal. Whitespace flexible. */
    public static TrackPosition parse(String text) {
        if (text == null) throw new IllegalArgumentException("null");
        String s = text.trim();
        // Expected forms: "3 12+345" or "3 12+345.67"
        String[] parts = s.split("\\s+");
        if (parts.length != 2) throw new IllegalArgumentException("Expected: 's km+m'");
        int section = Integer.parseInt(parts[0]);
        String[] kmM = parts[1].split("\\+");
        if (kmM.length != 2) throw new IllegalArgumentException("Expected: 's km+m'");
        int km = Integer.parseInt(kmM[0]);
        double m = Double.parseDouble(kmM[1]);
        return new TrackPosition(section, km, m).normalized();
    }

    @Override public String toString() { return format(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackPosition)) return false;
        TrackPosition tp = (TrackPosition) o;
        return section == tp.section && km == tp.km && Double.doubleToLongBits(m) == Double.doubleToLongBits(tp.m);
    }

    @Override public int hashCode() {
        long bits = Double.doubleToLongBits(m);
        int h = Integer.hashCode(section);
        h = 31 * h + Integer.hashCode(km);
        h = 31 * h + (int)(bits ^ (bits >>> 32));
        return h;
    }
}
