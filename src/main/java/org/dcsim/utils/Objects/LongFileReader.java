package org.longtable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * LongFileReader
 * - Minimal, beroendefri läsare för "longtable"-CSV (som skrivs via LongTableWriter/LongFileWriter).
 * - Returnerar Meta (project/scenario/hash) + List<Signal>.
 * - Klarar citattecken och autodetekterar delimiter (',' eller ';').
 *
 * Antagen kolumnordning (anpassa här om din LongTableWriter skriver annat):
 * time_s, objectType, objectId, signal, value, unit, stage, iter, note
 *
 * RUN-meta antas ligga som rader:
 *   objectType="RUN", objectId="meta", signal in {"project","scenario","hash_tag"}, note innehåller värdet.
 */
public final class LongFileReader {

    public static final class Meta {
        public final String project;
        public final String scenario;
        public final String hashTag;
        Meta(String project, String scenario, String hashTag) {
            this.project = project; this.scenario = scenario; this.hashTag = hashTag;
        }
        @Override public String toString() {
            return "Meta{project=" + project + ", scenario=" + scenario + ", hash_tag=" + hashTag + "}";
        }
    }

    public static final class Signal {
        public final Double time_s;
        public final String objectType;
        public final String objectId;
        public final String signal;
        public final Double value;
        public final String unit;
        public final String stage;
        public final Integer iter;
        public final String note;
        Signal(Double time_s, String objectType, String objectId, String signal,
               Double value, String unit, String stage, Integer iter, String note) {
            this.time_s = time_s; this.objectType = objectType; this.objectId = objectId;
            this.signal = signal; this.value = value; this.unit = unit;
            this.stage = stage; this.iter = iter; this.note = note;
        }
    }

    public static final class Data {
        public final Meta meta;
        public final List<Signal> signals;
        Data(Meta meta, List<Signal> signals) { this.meta = meta; this.signals = signals; }
    }

    private LongFileReader() {}

    public static Data read(Path csvPath) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            String header = br.readLine();
            if (header == null) return new Data(new Meta(null, null, null), List.of());

            char delim = guessDelimiter(header);
            int[] ix = mapColumns(header, delim);

            String line;
            String project = null, scenario = null, hash = null;
            List<Signal> list = new ArrayList<>(1024);

            while ((line = br.readLine()) != null) {
                List<String> cols = parseCsvLine(line, delim);
                if (cols.isEmpty()) continue;
                String objectType = get(cols, ix[1]);
                if ("RUN".equals(objectType)) {
                    String objId = get(cols, ix[2]);
                    String key   = get(cols, ix[3]); // "project" / "scenario" / "hash_tag"
                    String note  = get(cols, ix[8]);
                    if ("meta".equals(objId)) {
                        if ("project".equals(key))  project  = note;
                        else if ("scenario".equals(key)) scenario = note;
                        else if ("hash_tag".equals(key)) hash = note;
                    }
                    continue; // hoppa RUN från signals-listan
                }
                Double time  = parseDouble(get(cols, ix[0]));
                String objId = get(cols, ix[2]);
                String sig   = get(cols, ix[3]);
                Double val   = parseDouble(get(cols, ix[4]));
                String unit  = get(cols, ix[5]);
                String stage = get(cols, ix[6]);
                Integer iter = parseInt(get(cols, ix[7]));
                String note  = get(cols, ix[8]);

                list.add(new Signal(time, objectType, objId, sig, val, unit, stage, iter, note));
            }

            return new Data(new Meta(project, scenario, hash), list);
        }
    }

    /* ---------- CSV utils ---------- */

    private static char guessDelimiter(String header) {
        int c = count(header, ',');
        int s = count(header, ';');
        return (s > c) ? ';' : ',';
    }

    private static int count(String s, char ch) {
        int n=0; for (int i=0;i<s.length();i++) if (s.charAt(i)==ch) n++; return n;
    }

    // Returnerar indexmapping för kolumner vi bryr oss om. Anta standardheader; fallback till positionsbaserad.
    private static int[] mapColumns(String header, char delim) {
        List<String> h = parseCsvLine(header, delim);
        Map<String,Integer> map = new HashMap<>();
        for (int i=0;i<h.size();i++) map.put(h.get(i).trim(), i);

        int time  = map.getOrDefault("time_s", -1);
        int objT  = map.getOrDefault("objectType", -1);
        int objId = map.getOrDefault("objectId", -1);
        int sig   = map.getOrDefault("signal", -1);
        int val   = map.getOrDefault("value", -1);
        int unit  = map.getOrDefault("unit", -1);
        int stage = map.getOrDefault("stage", -1);
        int iter  = map.getOrDefault("iter", -1);
        int note  = map.getOrDefault("note", -1);

        // Fallback: anta exakt ordning om headers saknas/okända
        if (time<0)  time  = 0;
        if (objT<0)  objT  = 1;
        if (objId<0) objId = 2;
        if (sig<0)   sig   = 3;
        if (val<0)   val   = 4;
        if (unit<0)  unit  = 5;
        if (stage<0) stage = 6;
        if (iter<0)  iter  = 7;
        if (note<0)  note  = 8;

        return new int[]{ time, objT, objId, sig, val, unit, stage, iter, note };
    }

    // Enkel CSV-parser som hanterar "quoted, commas" och ;.
    private static List<String> parseCsvLine(String line, char delim) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i=0;i<line.length();i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQ && i+1<line.length() && line.charAt(i+1) == '"') { // escaped quote
                    cur.append('"'); i++;
                } else {
                    inQ = !inQ;
                }
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

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.valueOf(s.trim()); } catch (Exception e) { return null; }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
    }
}
