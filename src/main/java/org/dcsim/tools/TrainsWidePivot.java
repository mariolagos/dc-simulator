package org.dcsim.tools;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Lång -> bred pivot för trains.csv:
 * time_s,train_id,req_W,P_W,pos_m,speed_mps
 * =>
 * time_s,Train1.req_W,Train1.P_W,...,Train2.req_W,...
 */
public final class TrainsWidePivot {

    private static final String SEP = ","; // ändra vid behov

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: TrainsWidePivot <input-trains.csv> <output-trains_wide.csv>");
            System.exit(1);
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);

        List<String> lines = Files.readAllLines(in);
        if (lines.isEmpty()) {
            System.err.println("Empty trains.csv");
            return;
        }

        // Första rad: header
        String header = lines.get(0);
        String[] cols = header.split("\t|,");

        int idxTime  = indexOf(cols, "time_s");
        int idxId    = indexOf(cols, "train_id");
        int idxReq   = indexOf(cols, "req_W");
        int idxP     = indexOf(cols, "P_W");
        int idxPos   = indexOf(cols, "pos_m");
        int idxSpeed = indexOf(cols, "speed_mps");

        if (idxTime < 0 || idxId < 0) {
            throw new IllegalStateException("Missing time_s or train_id columns in trains.csv");
        }

        // time_s -> (trainId -> [req, P, pos, speed])
        Map<Integer, Map<String, double[]>> byTime = new TreeMap<>();
        Set<String> trainIds = new TreeSet<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] f = line.split("\t|,");
            if (f.length <= Math.max(idxSpeed, Math.max(idxPos, idxP))) continue;

            int t = (int) Double.parseDouble(f[idxTime]);
            String id = f[idxId].trim();
            if (id.isEmpty()) continue;

            double req   = parseOrZero(f, idxReq);
            double pNet  = parseOrZero(f, idxP);
            double pos   = parseOrZero(f, idxPos);
            double speed = parseOrZero(f, idxSpeed);

            trainIds.add(id);
            byTime.computeIfAbsent(t, k -> new HashMap<>())
                    .put(id, new double[]{ req, pNet, pos, speed });
        }

        // Skriv bred CSV
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            // Header
            StringBuilder h = new StringBuilder();
            h.append("time_s");
            for (String id : trainIds) {
                h.append(SEP).append(id).append(".req_W");
                h.append(SEP).append(id).append(".P_W");
                h.append(SEP).append(id).append(".pos_m");
                h.append(SEP).append(id).append(".speed_mps");
            }
            w.write(h.toString());
            w.newLine();

            // Rader
            for (Map.Entry<Integer, Map<String, double[]>> e : byTime.entrySet()) {
                int t = e.getKey();
                Map<String, double[]> perTrain = e.getValue();

                StringBuilder row = new StringBuilder();
                row.append(t);
                for (String id : trainIds) {
                    double[] vals = perTrain.get(id);
                    if (vals == null) {
                        row.append(SEP).append("0");
                        row.append(SEP).append("0");
                        row.append(SEP).append("0");
                        row.append(SEP).append("0");
                    } else {
                        row.append(SEP).append(fmt(vals[0]));
                        row.append(SEP).append(fmt(vals[1]));
                        row.append(SEP).append(fmt(vals[2]));
                        row.append(SEP).append(fmt(vals[3]));
                    }
                }
                w.write(row.toString());
                w.newLine();
            }
        }

        System.out.println("Wrote wide trains CSV to " + out);
    }

    private static int indexOf(String[] cols, String name) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].trim().equals(name)) return i;
        }
        return -1;
    }

    private static double parseOrZero(String[] f, int idx) {
        if (idx < 0 || idx >= f.length) return 0.0;
        String s = f[idx].trim();
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static String fmt(double v) {
        // enkelt format, Excel klarar det
        if (Math.abs(v) < 1e-9) return "0";
        return Double.toString(v);
    }
}
