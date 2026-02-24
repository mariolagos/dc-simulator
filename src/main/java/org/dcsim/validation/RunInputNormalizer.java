package org.dcsim.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RunInputNormalizer {

    public enum Mode {
        REQUIRE_SORTED,   // A
        SORT_ON_READ      // B
    }

    private final Mode mode;

    public RunInputNormalizer(Mode mode) {
        this.mode = mode;
    }

    /**
     * Returns normalized rows (possibly sorted). Throws on invalid input.
     */
    public List<Map<String, String>> normalizeAndValidate(List<Map<String, String>> rows) {
        Objects.requireNonNull(rows);

        // Basic parse + collect typed keys
        List<Row> parsed = new ArrayList<>(rows.size());
        int idx = 0;
        for (Map<String, String> r : rows) {
            idx++;
            parsed.add(Row.parse(r, idx));
        }

        // A or B
        if (mode == Mode.SORT_ON_READ) {
            Collections.sort(parsed);
        } else {
            assertAlreadySorted(parsed);
        }

        // Strongly recommended in both modes:
        assertNoDuplicateTrainTime(parsed);

        // Return rows in normalized order
        List<Map<String, String>> out = new ArrayList<>(parsed.size());
        for (Row rr : parsed) out.add(rr.raw);
        return out;
    }

    private static void assertAlreadySorted(List<Row> rows) {
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).compareTo(rows.get(i - 1)) < 0) {
                throw new ValidationInputException(
                        "run.csv not sorted: row=" + rows.get(i).rowIndex
                                + " time=" + rows.get(i).time
                                + " previousTime=" + rows.get(i - 1).time);
            }
        }
    }

    private static void assertNoDuplicateTrainTime(List<Row> rows) {
        Set<String> seen = new HashSet<>();
        for (Row r : rows) {
            String key = r.trainId + "@" + r.time;
            if (!seen.add(key)) {
                throw new ValidationInputException(
                        "run.csv duplicate (train_id,time_s) at row=" + r.rowIndex
                                + " train=" + r.trainId + " time=" + r.time);
            }
        }
    }

    // Deterministic order: time asc, train_id asc
    private static final class Row implements Comparable<Row> {
        final Map<String, String> raw;
        final int rowIndex;
        final double time;
        final String trainId;

        private Row(Map<String, String> raw, int rowIndex, double time, String trainId) {
            this.raw = raw;
            this.rowIndex = rowIndex;
            this.time = time;
            this.trainId = trainId;
        }

        static Row parse(Map<String, String> raw, int rowIndex) {
            String ts = raw.get("time_s");
            String trainId = raw.get("train_id");
            if (trainId == null) {
                throw new ValidationInputException("run.csv missing train_id at row=" + rowIndex);
            }
            double t;
            try {
                t = Double.parseDouble(ts);
            } catch (Exception e) {
                throw new ValidationInputException("run.csv invalid time at row=" + rowIndex + " value=" + ts);
            }
            return new Row(raw, rowIndex, t, trainId);
        }

        @Override
        public int compareTo(Row o) {
            int c = Double.compare(this.time, o.time);
            if (c != 0) return c;
            return this.trainId.compareTo(o.trainId);
        }
    }
}
