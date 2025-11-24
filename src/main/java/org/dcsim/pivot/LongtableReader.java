package org.dcsim.pivot;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.typesafe.config.Config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class LongtableReader implements Iterable<Record>, AutoCloseable {
    private final Reader in;                   // <— döp om för att undvika “reader”-förväxlingar
    private final CSVReader csv;
    private final Map<String,Integer> idx;
    private final Map<String,Integer> lowerIdx;
    private final Map<String,String> col;      // final: initieras i konstruktorn
    private String[] next;

    LongtableReader(Path csvPath, Config conf) throws IOException {
        this.in  = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
        this.csv = new CSVReaderBuilder(in).withSkipLines(0).build();

        String[] header;
        try {
            header = csv.readNext();
        } catch (CsvValidationException e) {
            throw new IOException("Invalid CSV header in " + csvPath, e);
        }
        if (header == null) throw new IOException("Empty longtable: " + csvPath);

        this.idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            idx.put(header[i].trim(), i);
        }
        this.col = ConfigUtil.columnMap(conf);  // <-- final fält initieras här

        this.lowerIdx = new HashMap<>();
        for (var e : idx.entrySet()) {
            lowerIdx.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
        if (Boolean.getBoolean("pivot.verbose")) {
            System.out.println("[Pivot/Reader] header = " + String.join(", ", idx.keySet()));
        }
        advance(); // läs första data-raden
    }

    private void advance() {
        try {
            next = csv.readNext();
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Failed reading CSV row", e);
        }
    }

    private static Double parseD(String[] row, Integer j) {
        if (j == null || j < 0 || j >= row.length) return null;
        String s = row[j] == null ? "" : row[j].trim();
        if (s.isEmpty()) return null;
        try { return Double.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    private static String parseS(String[] row, Integer j) {
        if (j == null || j < 0 || j >= row.length) return null;
        String s = row[j];
        return (s == null || s.isEmpty()) ? null : s;
    }

    private Integer c(String logical) {
        String physical = col.getOrDefault(logical, logical);
        Integer j = idx.get(physical);
        if (j != null) return j;
        // prova case-insensitivt
        j = lowerIdx.get(physical.toLowerCase(Locale.ROOT));
        if (j != null) return j;
        // sista chans: om mappingen saknas, prova själva logical-namnet i lower-case
        return lowerIdx.get(logical.toLowerCase(Locale.ROOT));
    }
    private Record readOne(String[] row) {
        Record r = new Record();
        r.time_s = Optional.ofNullable(parseD(row, c("time"))).orElse(0d);

// Hämta och normalisera kind
        String kindRaw = Optional.ofNullable(parseS(row, c("kind"))).orElse("");
        String ku = kindRaw.toUpperCase(Locale.ROOT);
        // Tolerant match: tillåt "Train", "Node", "RUN", osv
        if (ku.contains("NODE")) {
            r.kind = Record.Kind.NODE;
        } else if (ku.contains("LINE")) {
            r.kind = Record.Kind.LINE;
        } else if (ku.contains("TRAIN")) {
            r.kind = Record.Kind.TRAIN;
        } else if (ku.contains("RUN")) {
            r.kind = Record.Kind.RUN;      // <-- NYTT
        } else {
            r.kind = Record.Kind.UNKNOWN;  // <-- NYTT
        }

        r.id = Optional.ofNullable(parseS(row, c("id"))).orElse("");

        r.project  = parseS(row, c("project"));
        r.scenario = parseS(row, c("scenario"));
        r.hash_tag = parseS(row, c("hash"));

        r.V_V      = parseD(row, c("V"));
        r.I_A      = parseD(row, c("I"));
        r.P_W      = parseD(row, c("P"));
        r.P_loss_W = parseD(row, c("P_loss"));
        r.V_A_V    = parseD(row, c("VA"));
        r.V_B_V    = parseD(row, c("VB"));
        r.req_W    = parseD(row, c("req"));
        r.pos_m    = parseD(row, c("pos"));
        r.speed_mps= parseD(row, c("speed"));

        // Beräkna P om saknas men V och I finns
        if (r.P_W == null && r.V_V != null && r.I_A != null) {
            r.P_W = r.V_V * r.I_A;
        }
        return r;
    }

    @Override public void close() throws IOException {
        try { csv.close(); } finally { in.close(); }
    }

    @Override public Iterator<Record> iterator() {
        return new Iterator<Record>() {
            @Override public boolean hasNext() { return next != null; }
            @Override public Record next() {
                String[] row = LongtableReader.this.next;
                if (row == null) throw new NoSuchElementException();
                // förbered nästa
                advance();
                return readOne(row);
            }
        };
    }
}
