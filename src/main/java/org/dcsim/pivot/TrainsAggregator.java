package org.dcsim.pivot;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;

final class TrainsAggregator implements AutoCloseable {
    private final CSVWriter w;

    TrainsAggregator(Path out) throws IOException {
        this.w = Csvs.openCsv(out);
        w.writeNext(new String[]{"time_s","train_id","req_W","P_W","pos_m","speed_mps"});
    }

    void accept(Record r) {
        if (r.kind != Record.Kind.TRAIN) return;
        String t = Csvs.f(r.time_s);
        String req = r.req_W==null?"":Csvs.f(r.req_W);
        String p = r.P_W==null?"":Csvs.f(r.P_W);
        String pos = r.pos_m==null?"":Csvs.f(r.pos_m);
        String spd = r.speed_mps==null?"":Csvs.f(r.speed_mps);
        w.writeNext(new String[]{t, r.id==null?"":r.id, req, p, pos, spd});
    }

    @Override public void close() throws Exception { w.close(); }
}