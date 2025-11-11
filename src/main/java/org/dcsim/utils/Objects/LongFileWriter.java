package org.longtable;

import java.util.Objects;

/**
 * LongFileWriter
 * - Generisk, återanvändbar writer för "longtable"-format.
 * - Skriver RUN-meta i konstruktorn.
 * - Anropar putSignal(...) för vanliga rader.
 * - close() för att stänga underliggande resurs.
 *
 * Denna klass är neutral. Den använder en pluggbar "Sink" så att olika implementationer
 * kan stå under (ex: dc-simulatorns org.dcsim.export.LongTableWriter via Adapter).
 */
public final class LongFileWriter implements AutoCloseable {

    /** Abstrakt "sink" som kan ta emot en rad i longtable-format. */
    public interface Sink extends AutoCloseable {
        void signalRow(
                Double time_s, String objectType, String objectId,
                String signal, Double value, String unit,
                String stage, Integer iter, String note
        );
        @Override default void close() throws Exception {}
    }

    /** Adapter mot dc-simulators LongTableWriter utan att läcka beroenden uppåt. */
    public static final class DcsimSink implements Sink {
        private final Object delegate; // org.dcsim.export.LongTableWriter
        private final java.lang.reflect.Method mSignalRow;
        private final java.lang.reflect.Method mClose;

        @SuppressWarnings("unchecked")
        public DcsimSink(String path, boolean overwrite, String project, String scenario, String hash) {
            try {
                Class<?> clazz = Class.forName("org.dcsim.export.LongTableWriter");
                this.delegate = clazz
                        .getConstructor(String.class, boolean.class, String.class, String.class, String.class)
                        .newInstance(path, overwrite, project, scenario, hash);
                this.mSignalRow = clazz.getMethod("signalRow",
                        Double.class, String.class, String.class, String.class, Double.class, String.class,
                        String.class, Integer.class, String.class);
                this.mClose = clazz.getMethod("close");
            } catch (Exception e) {
                throw new RuntimeException("Failed to bind to org.dcsim.export.LongTableWriter", e);
            }
        }

        @Override
        public void signalRow(Double time_s, String objectType, String objectId, String signal, Double value,
                              String unit, String stage, Integer iter, String note) {
            try {
                mSignalRow.invoke(delegate, time_s, objectType, objectId, signal, value, unit, stage, iter, note);
            } catch (Exception e) {
                throw new RuntimeException("signalRow failed", e);
            }
        }

        @Override
        public void close() {
            try { mClose.invoke(delegate); } catch (Exception ignore) {}
        }
    }

    private final Sink sink;

    /**
     * Skapar en LongFileWriter ovanpå given Sink.
     * Skriver RUN-meta direkt.
     */
    public LongFileWriter(Sink sink,
                          String project, String scenario, String hash,
                          double t0ForMeta, String stageForMeta) {
        this.sink = Objects.requireNonNull(sink, "sink");
        // RUN-meta (lägger texten i note-kolumnen)
        sink.signalRow(t0ForMeta, "RUN", "meta", "project",  0.0, "", stageForMeta, null, project);
        sink.signalRow(t0ForMeta, "RUN", "meta", "scenario", 0.0, "", stageForMeta, null, scenario);
        sink.signalRow(t0ForMeta, "RUN", "meta", "hash_tag", 0.0, "", stageForMeta, null, hash);
    }

    /** Hjälpfabrik: bygg en writer direkt mot dc-simulatorns LongTableWriter. */
    public static LongFileWriter forDcsim(String csvPath, boolean overwrite,
                                          String project, String scenario, String hash,
                                          double t0ForMeta, String stageForMeta) {
        return new LongFileWriter(new DcsimSink(csvPath, overwrite, project, scenario, hash),
                project, scenario, hash, t0ForMeta, stageForMeta);
    }

    /** Skriv en vanlig signalrad. */
    public void putSignal(double time_s, String objectType, String objectId,
                          String signal, double value, String unit,
                          String stage, Integer iter, String note) {
        sink.signalRow(time_s, objectType, objectId, signal, value, unit, stage, iter, note);
    }

    @Override
    public void close() {
        try { sink.close(); } catch (Exception ignore) {}
    }
}
