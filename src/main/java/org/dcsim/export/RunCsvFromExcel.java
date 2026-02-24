package org.dcsim.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.validation.CsvSchema;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunCsvFromExcel {

    private RunCsvFromExcel() {
    }

    // Required output keys for RunCsvWriter schema (headers):
    private static final String K_TIME = "time_s";
    private static final String K_TRAIN = "train_id";
    private static final String K_SECTION = "section";
    private static final String K_TRACK = "track";
    private static final String K_POS = "position_m";
    private static final String K_P = "P_req_W";

    public static void writeRunCsv(Path excelXlsx, Path outRunCsv, String trainId) throws Exception {
        List<Map<String, String>> rows = readFullRunRows(excelXlsx, trainId);
        CsvSchema schema = CsvSchema.runSchema();
        RunCsvWriter writer = new RunCsvWriter(schema);
        writer.write(outRunCsv, rows);
    }
    /*
    CsvSchema schema = CsvSchema.runSchema();   // eller hur ni skapar den
RunCsvWriter writer = new RunCsvWriter(schema);
writer.write(outRunCsv, rows);
     */

    public static List<Map<String, String>> readFullRunRows(Path excelXlsx, String trainId) throws Exception {
        try (InputStream in = Files.newInputStream(excelXlsx);
             Workbook wb = new XSSFWorkbook(in)) {

            Sheet shRun = wb.getSheet("run");
            Sheet shTrack = wb.getSheet("track");
            if (shRun == null || shTrack == null) {
                throw new IllegalArgumentException("Workbook must contain sheets named 'run' and 'track': " + excelXlsx);
            }

            TrackIndex trackIdx = readTrackIndex(shTrack);
            return readRunSheet(shRun, trackIdx, trainId);
        }
    }

    private static TrackIndex readTrackIndex(Sheet shTrack) {
        Iterator<Row> it = shTrack.rowIterator();
        if (!it.hasNext()) throw new IllegalArgumentException("Empty 'track' sheet");

        Row header = it.next();
        Map<String, Integer> col = headerIndex(header);

        int cPos = requireCol(col, "position [m]");
        int cSection = requireCol(col, "trackSection");
        int cTrackNum = requireCol(col, "trackNumber");

        List<Double> pos = new ArrayList<>();
        List<String> section = new ArrayList<>();
        List<String> track = new ArrayList<>();

        while (it.hasNext()) {
            Row r = it.next();
            if (r == null) continue;

            Cell p = r.getCell(cPos);
            if (p == null || p.getCellType() != CellType.NUMERIC) continue;

            pos.add(p.getNumericCellValue());
            section.add(cellToString(r.getCell(cSection)).trim());
            track.add(cellToString(r.getCell(cTrackNum)).trim());
        }

        if (pos.isEmpty()) throw new IllegalArgumentException("No numeric rows parsed in 'track' sheet");

        double[] posArr = pos.stream().mapToDouble(d -> d).toArray();
        return new TrackIndex(posArr, section.toArray(new String[0]), track.toArray(new String[0]));
    }

    private static List<Map<String, String>> readRunSheet(Sheet shRun, TrackIndex trackIdx, String trainId) {
        Iterator<Row> it = shRun.rowIterator();
        if (!it.hasNext()) throw new IllegalArgumentException("Empty 'run' sheet");

        Row header = it.next();
        Map<String, Integer> col = headerIndex(header);

        int cTime = requireCol(col, "time [s]");
        int cPos = requireCol(col, "position [m]");
        int cMot = requireCol(col, "primaryMotoringPower [kW]");
        int cBrk = requireCol(col, "primaryMotorBrakingPower [kW]");

        List<Map<String, String>> out = new ArrayList<>();
        while (it.hasNext()) {
            Row r = it.next();
            if (r == null) continue;

            Cell timeCell = r.getCell(cTime);
            Cell posCell = r.getCell(cPos);
            if (timeCell == null || posCell == null) continue;
            if (timeCell.getCellType() != CellType.NUMERIC || posCell.getCellType() != CellType.NUMERIC) continue;

            double timeS = timeCell.getNumericCellValue();
            double posM = posCell.getNumericCellValue();

            int idx = findTrackIdx(trackIdx.posM, posM);

            // Decisions:
            // section = trackSection, track = trackNumber
            String section = trackIdx.trackSection[idx];
            String track = trackIdx.trackNumber[idx];

            double motKW = numericOrZero(r.getCell(cMot));
            double brkKW = numericOrZero(r.getCell(cBrk));
            double pReqW = (motKW - brkKW) * 1000.0;

            Map<String, String> row = new LinkedHashMap<>();
            row.put(K_TIME, fmt(timeS));
            row.put(K_TRAIN, trainId);
            row.put(K_SECTION, section);
            row.put(K_TRACK, track);
            row.put(K_POS, fmt(posM));
            row.put(K_P, fmt(pReqW));
            out.add(row);
        }

        if (out.isEmpty()) throw new IllegalArgumentException("No numeric rows parsed in 'run' sheet");
        return out;
    }

    // ───────── helpers ─────────

    private static Map<String, Integer> headerIndex(Row headerRow) {
        Map<String, Integer> idx = new HashMap<>();
        for (Cell c : headerRow) {
            if (c == null) continue;
            String s = c.toString();
            if (s != null) idx.put(s.trim(), c.getColumnIndex());
        }
        return idx;
    }

    private static int requireCol(Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null) throw new IllegalArgumentException("Missing required column: '" + name + "'");
        return i;
    }

    private static String cellToString(Cell c) {
        return c == null ? "" : c.toString();
    }

    private static double numericOrZero(Cell c) {
        if (c == null) return 0.0;
        if (c.getCellType() == CellType.NUMERIC) return c.getNumericCellValue();
        String s = c.toString().trim();
        if (s.isEmpty()) return 0.0;
        return Double.parseDouble(s);
    }

    private static int findTrackIdx(double[] pos, double x) {
        int lo = 0, hi = pos.length - 1;
        if (x <= pos[0]) return 0;
        if (x >= pos[hi]) return hi;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (pos[mid] <= x) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    private static String fmt(double v) {
        // Keep it simple and locale-stable
        return Double.toString(v);
    }

    private record TrackIndex(double[] posM, String[] trackSection, String[] trackNumber) {
    }
}
