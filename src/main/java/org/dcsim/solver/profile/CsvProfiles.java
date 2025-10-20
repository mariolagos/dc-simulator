package org.dcsim.solver.profile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * CSV format:
 *   train_id,t_s,P_W
 *   T1,0, -100000
 *   T1,5, -80000
 *   T2,0,  120000
 *
 * Rows may be grouped by train_id in any order.
 */
public final class CsvProfiles {
    private CsvProfiles() {}

    public static Map<String, TrainProfile> load(Path csv) throws IOException {
        Map<String, List<double[]>> map = new LinkedHashMap<>();
        try (BufferedReader br = Files.newBufferedReader(csv)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                if (first && s.toLowerCase(Locale.ROOT).startsWith("train_id")) {
                    first = false; // header
                    continue;
                }
                first = false;
                String[] parts = s.split(",");
                if (parts.length < 3) continue;
                String id = parts[0].trim();
                double t = Double.parseDouble(parts[1].trim());
                double p = Double.parseDouble(parts[2].trim());
                map.computeIfAbsent(id, k -> new ArrayList<>()).add(new double[]{t, p});
            }
        }
        Map<String, TrainProfile> out = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            List<double[]> rows = e.getValue();
            rows.sort(Comparator.comparingDouble(a -> a[0]));
            double[] t = new double[rows.size()];
            double[] p = new double[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                t[i] = rows.get(i)[0];
                p[i] = rows.get(i)[1];
            }
            out.put(e.getKey(), new TrainProfile(t, p));
        }
        return out;
    }
}
