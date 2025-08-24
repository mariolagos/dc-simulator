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

/**
 * CSV-writer: can truncate or append; has append(...) and close().
 * Header is written lazily on the first append so that dynamically created TrainLoad
 * devices are included in the exported columns.
 */
public final class ResultCsvWriter implements Closeable, Flushable {

    private final GridModel model;
    private final File file;
    private BufferedWriter out;
    private boolean headerWritten = false;

    private List<Integer> nodeIds;
    private List<String> deviceIds;
    private List<String> trainIds; // subset of deviceIds that are trains

    public ResultCsvWriter(GridModel model, String outputPath) {
        this(model, outputPath, true);
    }

    public ResultCsvWriter(GridModel model, String outputPath, boolean truncateOnStart) {
        this.model = model;
        this.file = new File(outputPath);

        if (file.getParentFile() != null) file.getParentFile().mkdirs();

        final boolean append = !truncateOnStart;
        try {
            boolean fileExists = file.exists();
            boolean empty = !fileExists || file.length() == 0L;
            this.out = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, append), StandardCharsets.UTF_8)
            );
            // If appending to a non-empty file, assume header exists already.
            this.headerWritten = append && !empty;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open CSV for writing: " + outputPath, e);
        }
    }

    private void writeHeaderIfNeeded() throws IOException {
        if (headerWritten) return;

        // Capture current topology (after TrainLoad devices may have been created)
        this.nodeIds = new ArrayList<>(model.getNodeIds());
        this.deviceIds = new ArrayList<>(model.getDeviceIds());
        this.trainIds = new ArrayList<>();
        for (String did : deviceIds) {
            Device<Real> d = model.getDevice(did);
            if (d instanceof TrainLoad) trainIds.add(did);
        }

        List<String> cols = new ArrayList<>();
        cols.add("time");
        cols.add("step");

        // Node voltages
        for (int nid : nodeIds) cols.add("V(" + nid + ")");

        // Per-device signed powers (includes trains, lines, substations)
        for (String did : deviceIds) cols.add("P[" + did + "]");

        // Requested power for trains (if available in GridResult)
        for (String tid : trainIds) cols.add("P_req[" + tid + "]");

        // Brake resistor power per train (pseudo-device id tid + "#brake")
        for (String tid : trainIds) cols.add("P_brake[" + tid + "]");

        // Aggregates
        cols.add("P_substations_out");
        cols.add("P_trains");
        cols.add("P_lines");
        cols.add("P_brake");   // total brake resistor power (sum of per-train)
        cols.add("Balance");   // sum of (sub + trains + lines) — kept for compatibility
        cols.add("Mismatch");  // sub - trains - lines (≈ 0 if network balances)

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

        // Per-device power + aggregate sums
        double sumSub = 0.0, sumTrain = 0.0, sumLine = 0.0;
        for (String did : deviceIds) {
            Real p = res.getLatestDevicePower(did); // signed
            double pd = p.asDouble();
            row.add(fmt(pd));

            Device<Real> d = model.getDevice(did);
            if (d instanceof Substation)       sumSub  += pd;
            else if (d instanceof TrainLoad)   sumTrain += pd;
            else if (d instanceof Line)        sumLine  += pd;
        }

        // Requested power per train (informative; not used in mismatch)
        for (String tid : trainIds) {
            Real pr = res.getLatestDeviceRequestedPower(tid);
            row.add(fmt(pr.asDouble()));
        }

        // Brake resistor power per train (pseudo device "tid#brake") + total
        double sumBrake = 0.0;
        for (String tid : trainIds) {
            Real pb = res.getLatestDevicePower(tid + "#brake");
            double pbd = pb.asDouble();
            row.add(fmt(pbd));
            sumBrake += pbd;
        }

        // Aggregates
        double balance  = sumSub + sumTrain + sumLine; // kept for compatibility (network-only)
        double mismatch = sumSub - sumTrain - sumLine; // network balance check

        row.add(fmt(sumSub));
        row.add(fmt(sumTrain));
        row.add(fmt(sumLine));
        row.add(fmt(sumBrake));
        row.add(fmt(balance));
        row.add(fmt(mismatch));

        out.write(String.join(",", row));
        out.write("\n");
    }

    @Override
    public void flush() throws IOException {
        if (out != null) out.flush();
    }

    @Override
    public void close() throws IOException {
        if (out != null) {
            out.flush();
            out.close();
            out = null;
        }
    }

    private static String fmt(double v) {
        if (Math.abs(v) >= 0.01) return String.format(Locale.US, "%.6g", v);
        if (v == 0.0) return "0";
        return String.format(Locale.US, "%.3e", v);
    }
}
