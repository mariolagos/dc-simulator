package org.dcsim.pivot;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;

final class StationsAggregator implements AutoCloseable {
    private final CSVWriter w;

    StationsAggregator(Path out) throws IOException {
        this.w = Csvs.openCsv(out);
        w.writeNext(new String[]{"time_s","station_id","V_V","I_A","P_W"});
    }

    void accept(Record r) {
        if (r.kind != Record.Kind.NODE) return;
        String t = Csvs.f(r.time_s);
        String v = r.V_V==null?"":Csvs.f(r.V_V);
        String i = r.I_A==null?"":Csvs.f(r.I_A);
        String p = r.P_W==null?"":Csvs.f(r.P_W);
        w.writeNext(new String[]{t, r.id==null?"":r.id, v, i, p});
    }

    @Override public void close() throws Exception { w.close(); }
}
