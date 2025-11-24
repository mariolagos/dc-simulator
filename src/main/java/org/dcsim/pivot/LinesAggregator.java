package org.dcsim.pivot;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class LinesAggregator {
    static final class LineState {
        Double minV = null;
        final TrapzAccumulator loss = new TrapzAccumulator();
        final TrapzAccumulator avgVA = new TrapzAccumulator();
        final TrapzAccumulator avgVB = new TrapzAccumulator();
        final TrapzAccumulator timeAcc = new TrapzAccumulator();
    }

    private final Map<String, LineState> byId = new TreeMap<>();

    void accept(Record r) {
        LineState s = byId.computeIfAbsent(r.id, k -> new LineState());
        if (r.V_A_V != null && r.V_B_V != null) {
            double vMin = Math.min(r.V_A_V, r.V_B_V);
            s.minV = (s.minV == null) ? vMin : Math.min(s.minV, vMin);
            s.avgVA.accept(r.time_s, r.V_A_V);
            s.avgVB.accept(r.time_s, r.V_B_V);
            s.timeAcc.accept(r.time_s, 1.0);
        }
        if (r.P_loss_W != null) s.loss.accept(r.time_s, r.P_loss_W);
    }

    double sumLineLossJ() {
        return byId.values().stream().mapToDouble(ls -> ls.loss.getEnergyJ()).sum();
    }

    void write(Path out) throws IOException {
        try (CSVWriter w = Csvs.openCsv(out)) {
            w.writeNext(new String[]{"line_id","V_min_V","E_loss_J","V_A_mean_V","V_B_mean_V"});
            for (Map.Entry<String, LineState> e : byId.entrySet()) {
                String id = e.getKey();
                LineState s = e.getValue();
                double t = s.timeAcc.getEnergyJ(); // integrates 1.0 over time -> total duration
                String vAmean = (t > 0) ? Csvs.f(s.avgVA.getEnergyJ() / t) : "";
                String vBmean = (t > 0) ? Csvs.f(s.avgVB.getEnergyJ() / t) : "";
                String vmin   = (s.minV != null) ? Csvs.f(s.minV) : "";
                String eloss  = Csvs.f(s.loss.getEnergyJ());
                w.writeNext(new String[]{id, vmin, eloss, vAmean, vBmean});
            }
        }
    }
}
