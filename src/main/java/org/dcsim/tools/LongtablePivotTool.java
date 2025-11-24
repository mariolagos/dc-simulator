package org.dcsim.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * LongtablePivotTool (A2) – updated for explicit project/scenario/base_hash columns,
 * with trimmed string fields and robust object_type matching.
 *
 * Longtable header (current schema):
 *   time_s, project, scenario, base_hash, object_type, object_id, signal, value, unit, stage, iter, note
 *
 * Implemented:
 *  - Reads project/scenario/base_hash per row.
 *  - Filters by project/scenario/hash on a per-row basis.
 *  - Stations pivot: NODE → (time_s, station_id, V_V, I_A, P_W)
 *  - Trains pivot:   TRAIN → (time_s, train_id,  req_W, P_W, pos_m, speed_mps)
 *
 * Debug:
 *  - --debug prints header, detected meta, row counts and distribution by object_type.
 *  - Additional debug in pivots: count stations/trains and samples.
 */
public final class LongtablePivotTool {

    /* ===== CLI ===== */

    public static void main(String[] args) throws Exception {
        Config cfg = Config.fromArgs(args);

        System.out.println("[Pivot] in=" + cfg.input + " outDir=" + cfg.outDir
                + " project=" + or(cfg.project, "*")
                + " scenario=" + or(cfg.scenario, "*")
                + " hash=" + or(cfg.hash, "*")
                + " excel=" + cfg.excel
                + " debug=" + cfg.debug);

        // 1) Read longtable and split into meta + data rows
        LongTable lt = LongTable.read(cfg.input);

        if (cfg.debug) {
            System.out.println("[Pivot] Header columns: " + lt.headerColumns);
            System.out.println("[Pivot] Detected first-row meta: " + lt.metaDetected);
            System.out.println("[Pivot] Raw row count: " + lt.allRows.size());
            Map<String, Integer> byType = countByObjectType(lt.allRows);
            System.out.println("[Pivot] Rows by object_type: " + byType);
        }

        // 2) Build filter from CLI and detected meta (only for printing)
        Filter filter = Filter.from(cfg, lt.metaDetected);
        System.out.println("[Pivot] Using filter: " + filter);

        // 3) Filter rows per row.project/scenario/base_hash
        List<Row> rows = lt.filterByMeta(filter);

        if (cfg.debug) {
            System.out.println("[Pivot] Filtered row count: " + rows.size());
            Map<String, Integer> byTypeF = countByObjectType(rows);
            System.out.println("[Pivot] Filtered rows by object_type: " + byTypeF);
            if (rows.isEmpty()) {
                System.out.println("[Pivot][WARN] No rows after filter. " +
                        "Check that CLI project/scenario/hash match the CSV columns.");
            }
        }

        // 4) Ensure output directory
        Files.createDirectories(cfg.outDir);

        // 5) Write pivot CSVs
        PivotWriter.writeStationsCsv(cfg.outDir.resolve("stations.csv"), rows, cfg.debug);
        PivotWriter.writeTrainsCsv(cfg.outDir.resolve("trains.csv"), rows, cfg.debug);
        PivotWriter.writeLinesCsvHeaderOnly(cfg.outDir.resolve("lines.csv"));
        PivotWriter.writeEnergyCsvHeaderOnly(cfg.outDir.resolve("energy.csv"));

        System.out.println("[Pivot] Wrote CSVs to: " + cfg.outDir.toAbsolutePath());

        // 6) Excel (placeholder)
        if (cfg.excel) {
            System.out.println("[Pivot] Excel requested, but not yet implemented. Skipping for now.");
        }
    }

    private static Map<String, Integer> countByObjectType(List<Row> rows) {
        Map<String, Integer> m = new HashMap<>();
        for (Row r : rows) {
            String k = (r.objectType == null ? "" : r.objectType);
            m.put(k, m.getOrDefault(k, 0) + 1);
        }
        return m;
    }

    /* ===== Model ===== */

    /** One longtable row (schema aligned to current CSV). */
    static final class Row {
        final Double time_s;
        final String project;
        final String scenario;
        final String baseHash;
        final String objectType;
        final String objectId;
        final String signal;
        final Double value;
        final String unit;
        final String stage;
        final Integer iter;
        final String note;

        Row(Double time_s, String project, String scenario, String baseHash,
            String objectType, String objectId, String signal,
            Double value, String unit, String stage, Integer iter, String note) {
            this.time_s = time_s;
            this.project = project;
            this.scenario = scenario;
            this.baseHash = baseHash;
            this.objectType = objectType;
            this.objectId = objectId;
            this.signal = signal;
            this.value = value;
            this.unit = unit;
            this.stage = stage;
            this.iter = iter;
            this.note = note;
        }
    }

    /** Per-file meta, derived from the first data row (for debug/printing). */
    static final class RunMeta {
        final String project;
        final String scenario;
        final String hash;

        RunMeta(String project, String scenario, String hash) {
            this.project = project;
            this.scenario = scenario;
            this.hash = hash;
        }

        @Override public String toString() {
            return "RunMeta{project=" + project + ", scenario=" + scenario + ", hash=" + hash + "}";
        }
    }

    /** Input + flags. Parsed from CLI. */
    static final class Config {
        final Path input;
        final Path outDir;
        final String project;
        final String scenario;
        final String hash;
        final boolean excel;
        final boolean debug;

        Config(Path input, Path outDir, String project, String scenario, String hash,
               boolean excel, boolean debug) {
            this.input = input;
            this.outDir = outDir;
            this.project = project;
            this.scenario = scenario;
            this.hash = hash;
            this.excel = excel;
            this.debug = debug;
        }

        static Config fromArgs(String[] args) {
            Path input = Paths.get("output/longtable.csv");
            Path out = Paths.get("output/pivots");
            String project = null, scenario = null, hash = null;
            boolean excel = false, debug = false;

            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--in".equals(a) && i + 1 < args.length) {
                    input = Paths.get(args[++i]);
                } else if ("--out".equals(a) && i + 1 < args.length) {
                    out = Paths.get(args[++i]);
                } else if ("--project".equals(a) && i + 1 < args.length) {
                    project = args[++i];
                } else if ("--scenario".equals(a) && i + 1 < args.length) {
                    scenario = args[++i];
                } else if ("--hash".equals(a) && i + 1 < args.length) {
                    hash = args[++i];
                } else if ("--excel".equals(a)) {
                    excel = true;
                } else if ("--debug".equals(a)) {
                    debug = true;
                } else if ("--help".equals(a) || "-h".equals(a)) {
                    printHelpAndExit();
                }
            }
            return new Config(input, out, project, scenario, hash, excel, debug);
        }

        static void printHelpAndExit() {
            System.out.println(
                    "LongtablePivotTool\n" +
                            "Usage: java ... org.dcsim.tools.LongtablePivotTool [--in path] [--out dir] [--project P] [--scenario S] [--hash H] [--excel] [--debug]\n" +
                            "Defaults: --in output/longtable.csv  --out output/pivots\n" +
                            "Filters are applied to CSV columns: project, scenario, base_hash."
            );
            System.exit(0);
        }
    }

    /** Filter used to select records by project/scenario/hash. */
    static final class Filter {
        final String project;
        final String scenario;
        final String hash;

        Filter(String project, String scenario, String hash) {
            this.project = project;
            this.scenario = scenario;
            this.hash = hash;
        }
        static Filter from(Config cfg, RunMeta detected) {
            String proj = (cfg.project != null) ? cfg.project : (detected != null ? detected.project : null);
            String scen = (cfg.scenario != null) ? cfg.scenario : (detected != null ? detected.scenario : null);
            String hash = (cfg.hash != null) ? cfg.hash : (detected != null ? detected.hash : null);
            return new Filter(proj, scen, hash);
        }
        @Override public String toString() {
            return "{project=" + or(project, "*") + ", scenario=" + or(scenario, "*") + ", hash=" + or(hash, "*") + "}";
        }
    }

    /** Entire longtable file (header + meta + rows). */
    static final class LongTable {
        final List<String> headerColumns;
        final RunMeta metaDetected;
        final List<Row> allRows;

        LongTable(List<String> headerColumns, RunMeta metaDetected, List<Row> allRows) {
            this.headerColumns = headerColumns;
            this.metaDetected = metaDetected;
            this.allRows = allRows;
        }

        static LongTable read(Path input) throws IOException {
            if (!Files.exists(input)) {
                throw new IOException("Longtable file not found: " + input.toAbsolutePath());
            }
            try (BufferedReader br = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
                String header = br.readLine();
                if (header == null) {
                    return new LongTable(List.of(), null, List.of());
                }
                List<String> headerCols = parseCsvLine(header, ','); // assuming comma delimiter
                int[] ix = mapColumns(header);

                String line;
                ArrayList<Row> rows = new ArrayList<>(4096);
                RunMeta meta = null;

                while ((line = br.readLine()) != null) {
                    List<String> cols = parseCsvLine(line, ','); // assuming comma
                    Row r = toRow(cols, ix);
                    if (r == null) continue;

                    if (meta == null && (r.project != null || r.scenario != null || r.baseHash != null)) {
                        meta = new RunMeta(r.project, r.scenario, r.baseHash);
                    }
                    rows.add(r);
                }
                return new LongTable(headerCols, meta, rows);
            }
        }

        List<Row> filterByMeta(Filter f) {
            if (f == null ||
                    (f.project == null && f.scenario == null && f.hash == null)) {
                return allRows;
            }
            ArrayList<Row> out = new ArrayList<>(allRows.size());
            for (Row r : allRows) {
                if (f.project != null && !nz(f.project).equals(nz(r.project)))   continue;
                if (f.scenario != null && !nz(f.scenario).equals(nz(r.scenario))) continue;
                if (f.hash != null && !nz(f.hash).equals(nz(r.baseHash)))         continue;
                out.add(r);
            }
            return out;
        }
    }

    /* ===== Pivot writers ===== */

    static final class PivotWriter {

        /** Stations: NODE signals → time series per station_id. */
        static void writeStationsCsv(Path out, List<Row> rows, boolean debug) throws IOException {
            Map<String, TreeMap<Double, StationTuple>> byStation = new HashMap<>();

            int seenNode = 0;
            for (Row r : rows) {
                String ot = tidy(r.objectType);
                if (ot != null && ot.equalsIgnoreCase("RUN")) continue; // skip RUN rows
                if (ot == null || !ot.equalsIgnoreCase("NODE")) continue;
                seenNode++;
                if (r.time_s == null) continue;

                String s = nz(r.objectId);
                TreeMap<Double, StationTuple> series = byStation.computeIfAbsent(s, k -> new TreeMap<>());
                StationTuple t = series.computeIfAbsent(r.time_s, k -> new StationTuple());
                String sig = tidy(r.signal);
                if ("V_V".equals(sig))        t.v = r.value;
                else if ("I_A".equals(sig))   t.i = r.value;
                else if ("P_W".equals(sig))   t.p = r.value;
            }

            int stationCount = byStation.size();
            int pointCount = 0;
            for (TreeMap<Double, StationTuple> series : byStation.values()) {
                pointCount += series.size();
            }

            if (debug) {
                System.out.println("[Pivot] Stations: raw Node-rows=" + seenNode
                        + ", stations=" + stationCount + ", time-points=" + pointCount);
            }

            try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                bw.write("time_s,station_id,V_V,I_A,P_W\n");
                for (Map.Entry<String, TreeMap<Double, StationTuple>> e : byStation.entrySet()) {
                    String stationId = e.getKey();
                    for (Map.Entry<Double, StationTuple> t : e.getValue().entrySet()) {
                        Double time = t.getKey();
                        StationTuple st = t.getValue();
                        bw.write(nv(time)); bw.write(',');
                        bw.write(csv(stationId)); bw.write(',');
                        bw.write(nv(st.v)); bw.write(',');
                        bw.write(nv(st.i)); bw.write(',');
                        bw.write(nv(st.p)); bw.write('\n');
                    }
                }
            }
        }

        /** Trains: TRAIN signals → time series per train_id. */
        static void writeTrainsCsv(Path out, List<Row> rows, boolean debug) throws IOException {
            Map<String, TreeMap<Double, TrainTuple>> byTrain = new HashMap<>();

            int seenTrain = 0;
            for (Row r : rows) {
                String ot = tidy(r.objectType);
                if (ot != null && ot.equalsIgnoreCase("RUN")) continue;
                if (ot == null || !ot.equalsIgnoreCase("TRAIN")) continue;
                seenTrain++;
                if (r.time_s == null) continue;

                String id = nz(r.objectId);
                TreeMap<Double, TrainTuple> series = byTrain.computeIfAbsent(id, k -> new TreeMap<>());
                TrainTuple t = series.computeIfAbsent(r.time_s, k -> new TrainTuple());
                String sig = tidy(r.signal);
                if ("req_W".equals(sig))          t.reqW = r.value;
                else if ("P_W".equals(sig))       t.pW = r.value;
                else if ("pos_m".equals(sig))     t.posM = r.value;
                else if ("speed_mps".equals(sig)) t.v = r.value;
            }

            int trainCount = byTrain.size();
            int pointCount = 0;
            for (TreeMap<Double, TrainTuple> series : byTrain.values()) {
                pointCount += series.size();
            }

            if (debug) {
                System.out.println("[Pivot] Trains: raw Train-rows=" + seenTrain
                        + ", trains=" + trainCount + ", time-points=" + pointCount);
            }

            try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                bw.write("time_s,train_id,req_W,P_W,pos_m,speed_mps\n");
                for (Map.Entry<String, TreeMap<Double, TrainTuple>> e : byTrain.entrySet()) {
                    String trainId = e.getKey();
                    for (Map.Entry<Double, TrainTuple> t : e.getValue().entrySet()) {
                        Double time = t.getKey();
                        TrainTuple tr = t.getValue();
                        bw.write(nv(time)); bw.write(',');
                        bw.write(csv(trainId)); bw.write(',');
                        bw.write(nv(tr.reqW)); bw.write(',');
                        bw.write(nv(tr.pW));   bw.write(',');
                        bw.write(nv(tr.posM)); bw.write(',');
                        bw.write(nv(tr.v));    bw.write('\n');
                    }
                }
            }
        }

        static void writeLinesCsvHeaderOnly(Path out) throws IOException {
            try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                bw.write("line_id,V_min_V,E_loss_J,V_A_mean_V,V_B_mean_V\n");
            }
        }

        static void writeEnergyCsvHeaderOnly(Path out) throws IOException {
            try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                bw.write("project,scenario,hash_tag,E_trac_J,E_aux_J,E_regen_J,E_burn_J,E_line_J,E_subs_J,E_total_J\n");
            }
        }
    }

    /* ===== Small holder tuples ===== */

    static final class StationTuple {
        Double v; // V_V
        Double i; // I_A
        Double p; // P_W
    }

    static final class TrainTuple {
        Double reqW; // req_W
        Double pW;   // P_W
        Double posM; // pos_m
        Double v;    // speed_mps
    }

    /* ===== CSV utils & helpers ===== */

    private static int[] mapColumns(String header) {
        List<String> h = parseCsvLine(header, ','); // assuming comma
        Map<String,Integer> m = new HashMap<>();
        for (int i = 0; i < h.size(); i++) m.put(h.get(i).trim(), i);

        int time    = m.getOrDefault("time_s", -1);
        int proj    = m.getOrDefault("project", -1);
        int scen    = m.getOrDefault("scenario", -1);
        int hash    = m.getOrDefault("base_hash", -1);
        int objT    = m.getOrDefault("object_type", -1);
        if (objT < 0) objT = m.getOrDefault("objectType", -1); // fallback if camelCase is used
        int objId   = m.getOrDefault("object_id", -1);
        if (objId < 0) objId = m.getOrDefault("objectId", -1);
        int sig     = m.getOrDefault("signal", -1);
        int val     = m.getOrDefault("value", -1);
        int unit    = m.getOrDefault("unit", -1);
        int stage   = m.getOrDefault("stage", -1);
        int iter    = m.getOrDefault("iter", -1);
        int note    = m.getOrDefault("note", -1);

        // fallback fixed positions if header mismatches
        if (time<0)  time  = 0;
        if (proj<0)  proj  = 1;
        if (scen<0)  scen  = 2;
        if (hash<0)  hash  = 3;
        if (objT<0)  objT  = 4;
        if (objId<0) objId = 5;
        if (sig<0)   sig   = 6;
        if (val<0)   val   = 7;
        if (unit<0)  unit  = 8;
        if (stage<0) stage = 9;
        if (iter<0)  iter  = 10;
        if (note<0)  note  = 11;

        return new int[]{ time, proj, scen, hash, objT, objId, sig, val, unit, stage, iter, note };
    }

    private static Row toRow(List<String> cols, int[] ix) {
        if (cols == null || cols.isEmpty()) return null;
        try {
            Double time   = parseD(get(cols, ix[0]));
            String proj   = tidy(get(cols, ix[1]));
            String scen   = tidy(get(cols, ix[2]));
            String hash   = tidy(get(cols, ix[3]));
            String objT   = tidy(get(cols, ix[4]));
            String objId  = tidy(get(cols, ix[5]));
            String sig    = tidy(get(cols, ix[6]));
            Double val    = parseD(get(cols, ix[7]));
            String unit   = tidy(get(cols, ix[8]));
            String stage  = tidy(get(cols, ix[9]));
            Integer iter  = parseI(get(cols, ix[10]));
            String note   = tidy(get(cols, ix[11]));
            return new Row(time, proj, scen, hash, objT, objId, sig, val, unit, stage, iter, note);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parseCsvLine(String line, char delim) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i=0;i<line.length();i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQ && i+1<line.length() && line.charAt(i+1) == '"') { cur.append('"'); i++; }
                else { inQ = !inQ; }
            } else if (ch == delim && !inQ) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String get(List<String> cols, int idx) {
        return (idx>=0 && idx<cols.size()) ? cols.get(idx) : null;
    }

    private static Double parseD(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.valueOf(s.trim()); } catch (Exception e) { return null; }
    }

    private static Integer parseI(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
    }

    private static String tidy(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nz(String s) { return (s == null) ? "" : s; }
    private static String or(String s, String fallback) { return (s == null || s.isBlank()) ? fallback : s; }

    private static String nv(Double d) {
        if (d == null || d.isNaN() || d.isInfinite()) return "";
        if (Math.abs(d) >= 1e12) return String.format(Locale.ROOT, "%.6e", d);
        return stripTrailingZeros(d);
    }

    private static String stripTrailingZeros(Double d) {
        String s = String.format(Locale.ROOT, "%.12f", d);
        int cut = s.length();
        while (cut > 0 && s.charAt(cut-1) == '0') cut--;
        if (cut > 0 && s.charAt(cut-1) == '.') cut--;
        return s.substring(0, Math.max(1, cut));
    }

    /** CSV escape helper: quotes field if needed, escapes internal quotes. */
    private static String csv(String s) {
        if (s == null) return "";
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needQuote) return s;
        String esc = s.replace("\"", "\"\"");
        return "\"" + esc + "\"";
    }
}
