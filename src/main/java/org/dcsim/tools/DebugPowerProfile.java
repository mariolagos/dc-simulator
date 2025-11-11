package org.dcsim.tools;

import org.dcsim.PowerPoint;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sanity-check och dump av inlästa PowerPoint-profiler.
 * Dumpar: time_s, P_W, V_V, I_A, pos, speed_mps
 */
public final class DebugPowerProfile {
    private DebugPowerProfile() {}

    /** Full dump och statistik till konsol + CSV. */
    public static void dumpProfile(String name, Collection<PowerPoint> points, Path outDir) throws IOException {
        Objects.requireNonNull(points, "points");
        Files.createDirectories(outDir);

        Path csv = outDir.resolve("profile_" + sanitizeFile(name) + ".csv");

        int n = points.size();
        double minT = points.stream().mapToDouble(PowerPoint::time).min().orElse(Double.NaN);
        double maxT = points.stream().mapToDouble(PowerPoint::time).max().orElse(Double.NaN);
        double sumAbsP = points.stream().mapToDouble(p -> Math.abs(p.power())).sum();
        long nzP = points.stream().filter(p -> p.power() != 0.0).count();

        System.out.printf(Locale.ROOT,
                "[Profile %s] n=%d, t=[%.3f..%.3f] s, |P| sum=%.6f, nonZeroP=%d%n",
                name, n, minT, maxT, sumAbsP, nzP);

        if (n == 0) System.err.printf("[Profile %s] !! TOM profil (fel blad/range/fil?)%n", name);
        if (Double.isNaN(minT) || Double.isNaN(maxT)) System.err.printf("[Profile %s] !! Tidsdomän saknas/NaN%n", name);
        if (sumAbsP == 0.0) System.err.printf("[Profile %s] !! Alla P_W = 0 (fel kolumn/enhet?)%n", name);

        // Enkla per-fält-statistik
        stat("V_V", points.stream().map(PowerPoint::voltage).collect(Collectors.toList()));
        stat("I_A", points.stream().map(PowerPoint::current).collect(Collectors.toList()));
        stat("speed_mps", points.stream().map(p -> p.hasSpeedMS() ? p.speedMS() : Double.NaN).collect(Collectors.toList()));

        try (BufferedWriter w = Files.newBufferedWriter(csv)) {
            w.write("time_s,P_W,V_V,I_A,pos,speed_mps\n");
            for (PowerPoint p : points) {
                String pos = safe(p.position());
                double spd = p.hasSpeedMS() ? p.speedMS() : Double.NaN;
                w.write(String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f,%s,%.6f%n",
                        p.time(), p.power(), p.voltage(), p.current(), csvSafe(pos), spd));
            }
        }
        System.out.println("Wrote: " + csv.toAbsolutePath());
    }

    /** Dumpa endast om tom/all-zero, annars skriv en kort OK-rad. */
    public static void dumpIfSuspicious(String name, Collection<PowerPoint> points, Path outDir) throws IOException {
        int n = points.size();
        double sumAbsP = points.stream().mapToDouble(p -> Math.abs(p.power())).sum();
        if (n == 0 || sumAbsP == 0.0) {
            dumpProfile(name, points, outDir);
        } else {
            double minT = points.stream().mapToDouble(PowerPoint::time).min().orElse(Double.NaN);
            double maxT = points.stream().mapToDouble(PowerPoint::time).max().orElse(Double.NaN);
            System.out.printf(Locale.ROOT,
                    "[Profile %s] OK-ish: n=%d, t=[%.3f..%.3f] s, |P| sum=%.6f (skip dump)%n",
                    name, n, minT, maxT, sumAbsP);
        }
    }

    // ---- helpers ----
    private static void stat(String label, List<Double> vals) {
        double nz = vals.stream().filter(v -> v != null && !v.isNaN() && v != 0.0).count();
        double min = vals.stream().filter(v -> v != null && !v.isNaN()).mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        double max = vals.stream().filter(v -> v != null && !v.isNaN()).mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        System.out.printf(Locale.ROOT, "  - %-10s nz=%d, range=[%.6f..%.6f]%n", label, (long) nz, min, max);
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String csvSafe(String s) { return safe(s).replace("\n", " ").replace("\r", " ").replace(",", ";"); }
    private static String sanitizeFile(String s) {
        if (s == null || s.isBlank()) return "profile";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
