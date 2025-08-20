package org.dcsim.export;

import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridResult;
import org.dcsim.electric.Line;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Simple CSV writer: supports truncate or append on start; has append(...) and close(). */
public final class ResultCsvWriter implements Closeable, Flushable {

    private final GridModel model;
    private final File file;
    private BufferedWriter out;
    private boolean headerWritten = false;

    private final List<Integer> nodeIds;
    private final List<String> deviceIds;

    /** Backwards compatible: truncates file on start (same as before). */
    public ResultCsvWriter(GridModel model, String outputPath) {
        this(model, outputPath, true);
    }

    /**
     * @param truncateOnStart if true, truncate/overwrite file when opening; if false, append.
     *                        When appending, header is written only if file is empty.
     */
    public ResultCsvWriter(GridModel model, String outputPath, boolean truncateOnStart) {
        this.model = model;
        this.file = new File(outputPath);
        this.nodeIds = new ArrayList<>(model.getNodeIds());
        this.deviceIds = new ArrayList<>(model.getDeviceIds());

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        final boolean append = !truncateOnStart;
        try {
            boolean fileExists = file.exists();
            boolean empty = !fileExists || file.length() == 0L;

            this.out = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, append), StandardCharsets.UTF_8)
            );

            // If we append to a NON-empty file, assume header already present
            // (otherwise we'll write it on first append).
            this.headerWritten = append && !empty;

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open CSV for writing: " + outputPath, e);
        }
    }

    private void writeHeaderIfNeeded() throws IOException {
        if (headerWritten) return;

        List<String> cols = new ArrayList<>();
        cols.add("time");
        cols.add("step");

        // Node voltages
        for (int nid : nodeIds) cols.add("V(" + nid + ")");

        // Per-device power (signed)
        for (String did : deviceIds) cols.add("P[" + did + "]");

        // Aggregates (optional)
        cols.add("P_substations_out");
        cols.add("P_trains");
        cols.add("P_lines");
        cols.add("Balance");

        out.write(String.join(",", cols));
        out.write("\n");
        headerWritten = true;
    }

    public void append(GridResult res, double timeSec, int step) throws IOException {
        writeHeaderIfNeeded();

        List<String> row = new ArrayList<>();
        row.add(fmt(timeSec));
        row.add(Integer.toString(step));

        // Voltages
        for (int nid : nodeIds) {
            Real v = res.getLatestNodeVoltage(nid);
            row.add(fmt(v.asDouble()));
        }

        // Per-device power + aggregates
        double sumSub = 0.0, sumTrain = 0.0, sumLine = 0.0;
        for (String did : deviceIds) {
            Real p = res.getLatestDevicePower(did); // signed
            row.add(fmt(p.asDouble()));

            Device<Real> d = model.getDevice(did);
            if (d instanceof Substation)    sumSub  += p.asDouble();
            else if (d instanceof TrainLoad) sumTrain += p.asDouble();
            else if (d instanceof Line)      sumLine  += p.asDouble();
        }

        double balance = sumSub + sumTrain + sumLine;
        row.add(fmt(sumSub));
        row.add(fmt(sumTrain));
        row.add(fmt(sumLine));
        row.add(fmt(balance));

        out.write(String.join(",", row));
        out.write("\n");
    }

    @Override public void flush() throws IOException { if (out != null) out.flush(); }
    @Override public void close() throws IOException { if (out != null) { out.flush(); out.close(); out = null; } }

    private static String fmt(double v) {
        if (Math.abs(v) >= 0.01) return String.format(Locale.US, "%.6g", v);
        if (v == 0.0) return "0";
        return String.format(Locale.US, "%.3e", v);
    }
}
