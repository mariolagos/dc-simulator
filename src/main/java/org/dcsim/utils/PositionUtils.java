package org.dcsim.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for parsing/formatting track positions and for chaining multiple sections.
 *
 * Canonical string form: "line km+meters" (e.g. "1 12+345").
 *
 * Backwards-compatible:
 *  - Keeps existing flexible parsing/formatting helpers.
 *  - Preserves zero-padded meters (3 digits) on format.
 *  - Distance/delta in meters continues to work as before.
 *
 * New (optional) features:
 *  - TrackPosition value type (line/section, km, m) with normalize/format.
 *  - SectionChain: convert between (line,km,m) and absolute meters along a chained route.
 *  - addDistance across section boundaries.
 */
public final class PositionUtils {

    private PositionUtils() {}

    // ─────────────────────────────────────────────────────────────────────────────
    // Existing regex-based parsing (kept as-is, with comments)
    // ─────────────────────────────────────────────────────────────────────────────

    private static final Pattern STRICT = Pattern.compile("(\\d+)\\s+(\\d+)\\+(\\d+)");

    // tolerant examples accepted: "1 12+345", "12+345", "1 12.345", "12.345km", "1 12.345 km"
    private static final Pattern FLEXIBLE = Pattern.compile(
            "(?:(\\d+)\\s+)?" +             // optional line
                    "(\\d+)(?:[.,](\\d+))?" +       // km (optional decimal; we TRUNCATE, not scale)
                    "\\s*(?:km)?\\s*" +
                    "(?:\\+\\s*(\\d+(?:[.,]\\d+)?))?" + // meters (OPTIONAL, may have decimals)
                    "\\s*(?:m)?"
    );

    // Lägg in i PositionUtils (samma klass som merged-filen)
    public static int[] parse(String pos) {
        // Behåll samma semantik som tidigare (troligen flexibel)
        return parseFlexible(pos);
    }

    // Om du ibland vill ha objekt i stället för int[]:
    public static TrackPosition parseTP(String pos) {
        return TrackPosition.parse(pos);
    }

    /** Parse "line km+mmm" strictly into {line, km, m}. */
    public static int[] parseStrict(String pos) {
        Matcher m = STRICT.matcher(pos.trim());
        if (!m.matches()) throw new IllegalArgumentException("Expected 'line km+mmm': " + pos);
        int line = Integer.parseInt(m.group(1));
        int km = Integer.parseInt(m.group(2));
        int meters = Integer.parseInt(m.group(3));
        if (meters >= 1000) {
            km += meters / 1000;
            meters = meters % 1000;
        }
        return new int[]{ line, km, meters };
    }

    /**
     * Flexible parse supporting:
     *  - "line km+mmm"
     *  - "km+mmm"  (assumes line=1)
     *  - "line km.m"
     *  - "km.m km"
     *  - with/without 'km'/'m' tokens; comma/period decimal
     */
    public static int[] parseFlexible(String pos) {
        String s = pos == null ? "" : pos.trim();
        Matcher m = FLEXIBLE.matcher(s);
        if (!m.matches()) throw new IllegalArgumentException("Unrecognized position: " + pos);

        int line = m.group(1) != null ? Integer.parseInt(m.group(1)) : 1;
        int km = Integer.parseInt(m.group(2));
        int metersFromDecimal = 0;
        if (m.group(3) != null && !m.group(3).isEmpty()) {
            // e.g. "12.345" -> +345 m (TRUNCATE, not scale)
            String dec = m.group(3);
            if (dec.length() > 3) dec = dec.substring(0, 3);
            metersFromDecimal = Integer.parseInt(dec);
        }

        int metersExplicit = 0;
        if (m.group(4) != null && !m.group(4).isEmpty()) {
            // e.g. "+009.99" -> 9 m (TRUNCATE decimals)
            String mtok = m.group(4).replace(',', '.');
            double md = Double.parseDouble(mtok);
            metersExplicit = (int) Math.floor(md);
        }

        int meters = (metersExplicit > 0) ? metersExplicit : metersFromDecimal;

        if (meters >= 1000) {
            km += meters / 1000;
            meters = meters % 1000;
        }
        return new int[]{ line, km, meters };    }

    /** Convert {line,km,m} to meters within the given line: km*1000+m. */
    public static double toMeters(int line, int km, int meters) {
        if (km < 0 || meters < 0) throw new IllegalArgumentException("Negative values not allowed");
        return km * 1000.0 + meters;
    }

    public static double toMeters(int[] lkm) {
        if (lkm == null || lkm.length < 3) throw new IllegalArgumentException("Expected {line,km,m}");
        return toMeters(lkm[0], lkm[1], lkm[2]);
    }

    public static double toMeters(String pos) {
        return toMeters(parseFlexible(pos));
    }

    /** Format "line km+mmm" with meters zero-padded to 3 digits (TRUNCATE only). */
    public static String format(int line, double meters) {
        if (meters < 0) throw new IllegalArgumentException("meters must be >= 0");
        int km = (int) Math.floor(meters / 1000.0);
        int m = (int) Math.floor(meters - km * 1000.0); // truncate, no rounding
        return String.format("%d %d+%03d", line, km, m);
    }

    /** Default line=1. */
    public static String format(double meters) {
        return format(1, meters);
    }

    /** Normalize any input string to canonical "line km+mmm". */
    public static String normalize(String pos) {
        int[] p = parseFlexible(pos);
        return String.format("%d %d+%03d", p[0], p[1], p[2]);
    }

    /** Absolute distance difference (meters) between two positions (same line interpretation). */
    public static double distance(String position, String position1) {
        return Math.abs(toMeters(parseFlexible(position)) - toMeters(parseFlexible(position1)));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // New: value type + section chain for multi-line/section routes
    // ─────────────────────────────────────────────────────────────────────────────

    /** Immutable value representing a position inside a line/section: line km+m. */
    public static final class TrackPosition {
        public final int line;
        public final int km;
        public final double m; // may carry decimals internally

        public TrackPosition(int line, int km, double m) {
            if (line < 0 || km < 0 || m < 0) throw new IllegalArgumentException("Negative values not allowed");
            this.line = line;
            this.km = km;
            this.m = m;
        }

        /** Returns km*1000 + m (raw, not truncated). */
        public double metersInLine() { return km * 1000.0 + m; }

        /** Normalize so that m < 1000 by carrying overflows into km. */
        public TrackPosition normalized() {
            int extraKm = (int) Math.floor(m / 1000.0);
            double newM = m - extraKm * 1000.0;
            return new TrackPosition(line, km + extraKm, newM);
        }

        /** "line km+mmm" with TRUNCATED meters (zero-padded 3 digits). */
        public String format() {
            int trunc = (int) Math.floor(m % 1000.0);
            return String.format("%d %d+%03d", line, km, trunc);
        }

        @Override public String toString() { return format(); }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TrackPosition)) return false;
            TrackPosition tp = (TrackPosition) o;
            return line == tp.line && km == tp.km && Double.doubleToLongBits(m) == Double.doubleToLongBits(tp.m);
        }

        @Override public int hashCode() {
            long bits = Double.doubleToLongBits(m);
            int h = Integer.hashCode(line);
            h = 31 * h + Integer.hashCode(km);
            h = 31 * h + (int)(bits ^ (bits >>> 32));
            return h;
        }

        /** Parse using the same flexible rules as string-based helpers. */
        public static TrackPosition parse(String text) {
            int[] p = parseFlexible(text);
            return new TrackPosition(p[0], p[1], p[2]).normalized();
        }
    }

    /** Represents one ordered section/line with a total length in meters. */
    public static final class Section {
        public final int id;              // typically "line" (s)
        public final double lengthMeters; // total meters in this section
        public Section(int id, double lengthMeters) {
            if (id < 0 || lengthMeters < 0) throw new IllegalArgumentException("Invalid section");
            this.id = id; this.lengthMeters = lengthMeters;
        }
    }

    /**
     * Chain of sections with prefix sums for fast conversion:
     *   (line,km,m) ↔ absolute meters along the full route.
     */
    public static final class SectionChain {
        private final List<Section> sections;
        private final double[] prefix;           // prefix[i] = sum length up to i (exclusive)
        private final Map<Integer, Integer> indexById;

        public SectionChain(List<Section> inOrder) {
            if (inOrder == null || inOrder.isEmpty())
                throw new IllegalArgumentException("sections must not be empty");
            this.sections = Collections.unmodifiableList(new ArrayList<>(inOrder));
            this.prefix = new double[sections.size() + 1];
            this.indexById = new HashMap<>();
            double sum = 0.0;
            for (int i = 0; i < sections.size(); i++) {
                Section s = sections.get(i);
                if (indexById.containsKey(s.id))
                    throw new IllegalArgumentException("duplicate section id: " + s.id);
                indexById.put(s.id, i);
                prefix[i] = sum;
                sum += s.lengthMeters;
            }
            prefix[sections.size()] = sum;
        }

        public List<Section> sections() { return sections; }
        public double totalLength() { return prefix[sections.size()]; }

        /** Convert TrackPosition to absolute meters along the chain. */
        public double toAbsolute(TrackPosition p) {
            TrackPosition n = p.normalized();
            Integer idx = indexById.get(n.line);
            if (idx == null) throw new IllegalArgumentException("Unknown section id: " + n.line);
            double inLine = n.metersInLine();
            double max = sections.get(idx).lengthMeters;
            if (inLine < 0.0 || inLine > max + 1e-9) {
                if (inLine > max + 1e-3) throw new IllegalArgumentException("Position exceeds section length");
            }
            return prefix[idx] + Math.min(inLine, max);
        }

        /** Convert absolute meters to TrackPosition in this chain. */
        public TrackPosition fromAbsolute(double absMeters) {
            if (absMeters < 0.0) absMeters = 0.0;
            if (absMeters > totalLength()) absMeters = totalLength();

            // binary search over prefix
            int lo = 0, hi = sections.size();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (prefix[mid] < absMeters) lo = mid + 1; else hi = mid;
            }
            int idx = Math.max(0, lo - 1);
            double base = prefix[idx];
            double offset = absMeters - base;
            Section sec = sections.get(idx);
            if (offset > sec.lengthMeters) offset = sec.lengthMeters;

            int km = (int) Math.floor(offset / 1000.0);
            double m = offset - km * 1000.0;
            return new TrackPosition(sec.id, km, m).normalized();
        }

        /** Add delta meters to a TrackPosition, traversing section boundaries if needed. */
        public TrackPosition addDistance(TrackPosition start, double deltaMeters) {
            double abs = toAbsolute(start) + deltaMeters;
            return fromAbsolute(abs);
        }
    }
}
