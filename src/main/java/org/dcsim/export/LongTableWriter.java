package org.dcsim.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

//TODO(v0.9): Introduce BaseSig/NiceSig split and per-signal verbosity flag in signalRow,
//and use config-driven selection for NiceSig in wide export.

public final class LongTableWriter implements Closeable, Flushable {
    private final BufferedWriter bw;
    private final String project, scenario, baseHash;

    public LongTableWriter(String path, boolean overwrite,
                           String project, String scenario, String baseHash) throws IOException {
        File f = new File(path);
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        if (overwrite) try (FileOutputStream trunc = new FileOutputStream(f, false)) { /* truncate */ }
        this.bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
        this.project = nz(project); this.scenario = nz(scenario); this.baseHash = nz(baseHash);
        if (overwrite || f.length() == 0) {
            bw.write("time_s,project,scenario,base_hash,object_type,object_id,signal,value,unit,stage,iter,note");
            bw.newLine(); bw.flush();
        }
    }

    public synchronized void signalRow(Double time_s, String objectType, String objectId,
                                       String signal, Double value, String unit,
                                       String stage, Integer iter, String note) {
        try {
            bw.write(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    nz(time_s), project, scenario, baseHash,
                    nz(objectType), nz(objectId), nz(signal), nz(value),
                    nz(unit), nz(stage), nz(iter), nz(note)
            ));
            bw.newLine();

            System.out.printf("[LongCSV] t=%.3f %s/%s %s=%s %s stage=%s%n",
                    time_s, objectType, objectId, signal,
                    value == null ? "" : String.format(java.util.Locale.ROOT,"%.3f", value),
                    unit == null ? "" : unit,
                    stage
            );
            bw.flush();
        } catch (IOException e) {
            System.err.println("[LongCSV] write failed: " + e.getMessage());
        }
    }

    @Override public synchronized void flush() throws IOException { bw.flush(); }
    @Override public synchronized void close() throws IOException { bw.flush(); bw.close(); }

    private static String nz(Object o) { return o == null ? "" : o.toString(); }
}
