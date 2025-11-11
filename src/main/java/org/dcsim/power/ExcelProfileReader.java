package org.dcsim.power;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.PowerPoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Läser .xlsx (eller katalog med .xlsx) med rubriker (case-insensitive).
 * Minimikrav: "time [s]".
 * Power tas från "P_W" eller "P [W]" om sådan kolumn finns; annars P_req = mot_W + aux_W − brk_W.
 * Position tas från "bisposition [km,m]" som text; hastighet från "speed [m/s]".
 */
public final class ExcelProfileReader {

    public static List<PowerPoint> read(java.io.File file) throws java.io.IOException {
        return read(file.toPath());
    }

    public static List<PowerPoint> read(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            List<PowerPoint> all = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, "*.xlsx")) {
                for (Path p : ds) all.addAll(readSingleFile(p.toFile()));
            }
            all.sort(Comparator.comparingDouble(PowerPoint::time));
            return all;
        }
        return readSingleFile(path.toFile());
    }

    private static List<PowerPoint> readSingleFile(File file) throws IOException {
        List<PowerPoint> out = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = chooseSheet(wb);
            if (sheet == null) return out;

            int headerRowIdx = findHeaderRow(sheet);
            if (headerRowIdx < 0) return out;
            Row header = sheet.getRow(headerRowIdx);
            Map<String, Integer> idx = indexHeaders(header);

            Integer tIx = idx.get("time [s]");
            if (tIx == null) return out;

            Integer posIx = firstPresent(idx, "bisposition [km,m]");
            Integer spdIx = firstPresent(idx, "speed [m/s]", "speed", "velocity", "v");
            Integer motIx = firstPresent(idx, "primarymotoringpower [kw]");
            Integer brkIx = firstPresent(idx, "primarymotorbrakingpower [kw]");

            int firstData = headerRowIdx + 1;
            for (int r = firstData; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Double t = getNumeric(row, tIx);
                if (t == null) continue;

                String posStr = posIx != null ? getString(row, posIx) : null;

                // speed in km/h; if missing -> NaN
                Double speedMs = spdIx != null ? getNumeric(row, spdIx) * 3.6 : null;

                // power in kW
                Double mot = (motIx != null) ? getOrZero(row, motIx) * 1000 : null;
                Double brk = (brkIx != null) ? getOrZero(row, brkIx) * 1000 : null;
                double pW = 0;
                if (mot != null && brk != null) {
                    pW = mot + brk;
                }

                PowerPoint pp = PowerPoint.ofPositionString(
                        t,
                        pW,
                        Double.NaN,
                        Double.NaN,
                        posStr,
                        (speedMs != null ? speedMs : Double.NaN)
                );

                out.add(pp);
            }
        }
        out.sort(Comparator.comparingDouble(PowerPoint::time));
        return out;
    }

    // ---------- helpers ----------

    private static Sheet chooseSheet(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String name = wb.getSheetName(i);
            if (name != null && name.trim().equalsIgnoreCase("data")) return wb.getSheetAt(i);
        }
        return wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
    }

    private static int findHeaderRow(Sheet sheet) {
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> m = indexHeaders(row);
            if (m.containsKey("time [s]")) return r;
        }
        return -1;
    }

    private static Map<String, Integer> indexHeaders(Row headerRow) {
        Map<String, Integer> m = new HashMap<>();
        if (headerRow == null) return m;
        short first = headerRow.getFirstCellNum();
        short last = headerRow.getLastCellNum();
        for (int c = first; c < last; c++) {
            Cell cell = headerRow.getCell(c);
            String s = (cell != null) ? getString(cell) : null;
            if (s == null || s.isBlank()) continue;
            m.put(s.trim().toLowerCase(Locale.ROOT), c);
        }
        return m;
    }

    private static Integer firstPresent(Map<String, Integer> m, String... keys) {
        for (String k : keys) {
            Integer ix = m.get(k);
            if (ix != null) return ix;
        }
        return null;
    }

    private static double getOrZero(Row row, Integer ix) {
        Double v = getNumeric(row, ix);
        return v == null ? 0.0 : v;
    }

    private static Double getNumeric(Row row, Integer colIx) {
        if (colIx == null) return null;
        Cell cell = row.getCell(colIx);
        if (cell == null) return null;
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return cell.getNumericCellValue();
                case STRING:
                    String s = cell.getStringCellValue();
                    if (s == null || s.isBlank()) return null;
                    return Double.parseDouble(s.trim());
                case FORMULA:
                    try {
                        return cell.getNumericCellValue();
                    } catch (Exception e) {
                        String fs = cell.getStringCellValue();
                        if (fs == null || fs.isBlank()) return null;
                        return Double.parseDouble(fs.trim());
                    }
                default:
                    return null;
            }
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String getString(Row row, Integer colIx) {
        if (colIx == null) return null;
        Cell cell = row.getCell(colIx);
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return Double.toString(cell.getNumericCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        return Double.toString(cell.getNumericCellValue());
                    } catch (Exception ignore) {
                        return null;
                    }
                }
            default:
                return null;
        }
    }

    private static String getString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return Double.toString(cell.getNumericCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        return Double.toString(cell.getNumericCellValue());
                    } catch (Exception ignore) {
                        return null;
                    }
                }
            default:
                return null;
        }
    }
}
