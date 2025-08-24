package org.dcsim.power;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.PowerPoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * ExcelProfileReader – läser kraftprofiler från .xlsx.
 * Klarar både att få en enskild fil OCH en katalog som innehåller flera .xlsx.
 * <p>
 * Förväntade kolumnrubriker (case-insensitive):
 * - time                 (sekunder)
 * - bisPosition          (t.ex. "1 0+550" eller "0.550")
 * - speed                (m/s)  [valfri]
 * - primaryMotoringPower (kW)
 * - primaryMotorBrakingPower (kW)
 * <p>
 * Output: List<PowerPoint>
 */
public final class ExcelProfileReader {

    private ExcelProfileReader() {
    }

    /**
     * Läs en fil ELLER en katalog (alla .xlsx i katalogen).
     */
    public static List<PowerPoint> read(File input) {
        if (input == null) return Collections.emptyList();

        if (input.isDirectory()) {
            File[] files = input.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".xlsx"));
            if (files == null || files.length == 0) {
                System.err.println("[ExcelProfileReader] No .xlsx files found in directory: " + input.getAbsolutePath());
                return Collections.emptyList();
            }
            List<PowerPoint> merged = new ArrayList<>();
            // sortera för deterministisk ordning
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                merged.addAll(readSingle(f));
            }
            return merged;
        } else if (input.isFile() && input.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return readSingle(input);
        } else {
            System.err.println("[ExcelProfileReader] Not a valid Excel file/dir: " + input.getAbsolutePath());
            return Collections.emptyList();
        }
    }

    /**
     * Läs en enskild .xlsx. Tar första arket.
     */
    private static List<PowerPoint> readSingle(File xlsx) {
        List<PowerPoint> out = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(xlsx);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) {
                System.err.println("[ExcelProfileReader] No sheets in: " + xlsx.getName());
                return out;
            }

            // Hitta header-rad (första 5–10 raderna; vanligtvis rad 0)
            int headerRowIdx = findHeaderRow(sheet);
            if (headerRowIdx < 0) {
                // Försök fast kolumnordning om rubriker saknas
                mapByFixedOrder(sheet, out);
                return out;
            }

            Row header = sheet.getRow(headerRowIdx);
            Map<String, Integer> colIx = mapHeader(header);

/*
            // Falla tillbaka på fast ordning om några nycklar saknas
            if (!colIx.containsKey("time") ||
                    !colIx.containsKey("bisposition") ||
                    !colIx.containsKey("primarymotoringpower") ||
                    !colIx.containsKey("primarymotorbrakingpower")) {
                mapByFixedOrder(sheet, out);
                return out;
            }
*/

            // valfri kolumn
            Integer speedIx = colIx.get("speed [m/s]");

            // Läs data-rader
            int firstData = headerRowIdx + 1;
            for (int r = firstData; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Double timeSec = getNumeric(row, colIx.get("time [s]"));
                if (timeSec == null) continue; // krävs

                String bisPos = getString(row, colIx.get("bisposition [km,m]"));
                if (bisPos == null || bisPos.isBlank()) bisPos = "0.0";

                Double speedMs = getNumeric(row, colIx.get("speed [m/s]"));
                if (speedMs == null) speedMs = 0.0;

                Double motKW = getNumeric(row, colIx.get("primarymotoringpower [kw]"));
                if (motKW == null) motKW = 0.0;

                Double brkKW = getNumeric(row, colIx.get("primarymotorbrakingpower [kw]"));
                if (brkKW == null) brkKW = 0.0;

                // Antag PowerPoint(time, bisPosition, speed, motoringKW, brakingKW)
                out.add(new PowerPoint(timeSec, bisPos, speedMs, (motKW + brkKW)*1000, 0., 0.));
            }

        } catch (IOException e) {
            System.err.println("[ExcelProfileReader] Failed to read " + xlsx.getAbsolutePath() + ": " + e);
        }
        return out;
    }

    // --- helpers ---

    /**
     * Försök hitta header-rad bland de första ~10 raderna.
     */
    private static int findHeaderRow(Sheet sheet) {
        int maxProbe = Math.min(10, sheet.getLastRowNum());
        for (int r = 0; r <= maxProbe; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String joined = joinLower(row);
            if (joined.contains("time") && (joined.contains("bis") || joined.contains("position"))) {
                return r;
            }
        }
        return -1;
    }

    /**
     * Mappar header-celler till index med case-insensitive nycklar.
     */
    private static Map<String, Integer> mapHeader(Row header) {
        Map<String, Integer> m = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            String key = normalizeHeader(cell);
            if (key == null) continue;

            // normalisera kända varianter
            if (
                    key.equals("time [s]") ||
                            key.equals("bisposition [km,m]") ||
                            key.equals("speed [m/s]") ||
                            key.equals("primarymotoringpower [kw]") ||
                            key.equals("primarymotorbrakingpower [kw]")
            ) {
                m.put(key, c);
            } ;
        }
        return m;
    }

    private static String normalizeHeader(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue();
            if (s != null) return s.trim().toLowerCase(Locale.ROOT);
        }
        // tillåt numeriska celler som header i nödfall (ovanligt)
        if (cell.getCellType() == CellType.NUMERIC) {
            return Double.toString(cell.getNumericCellValue()).trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static String joinLower(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                if (cell.getCellType() == CellType.STRING) {
                    sb.append(' ').append(cell.getStringCellValue().toLowerCase(Locale.ROOT));
                } else if (cell.getCellType() == CellType.NUMERIC) {
                    sb.append(' ').append(cell.getNumericCellValue());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Fallback: anta fast ordning på kolumnerna.
     */
    private static void mapByFixedOrder(Sheet sheet, List<PowerPoint> out) {
        // Anta rad 0 = header eller data; läs från rad 1 om header finns.
        int startRow = 0;
        Row maybeHeader = sheet.getRow(0);
        if (maybeHeader != null) {
            String joined = joinLower(maybeHeader);
            if (joined.contains("time") || joined.contains("bis") || joined.contains("motoring")) {
                startRow = 1;
            }
        }

        for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Double timeSec = getNumeric(row, 0);
            if (timeSec == null) continue;

            String bisPos = getString(row, 1);
            if (bisPos == null || bisPos.isBlank()) bisPos = "0.0";

            Double speedMs = getNumeric(row, 2);
            if (speedMs == null) speedMs = 0.0;

            Double motKW = getNumeric(row, 3);
            if (motKW == null) motKW = 0.0;

            Double brkKW = getNumeric(row, 4);
            if (brkKW == null) brkKW = 0.0;

            out.add(new PowerPoint(timeSec, bisPos, speedMs, motKW + brkKW, 0.0, 0.0));
        }
    }

    private static Double getNumeric(Row row, Integer col) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return null;
            try {
                return Double.parseDouble(s.replace(',', '.'));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private static String getString(Row row, Integer col) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            // tillåt numerisk bisPosition (t.ex. 0.550 km) → gör om till sträng
            double v = cell.getNumericCellValue();
            return Double.toString(v);
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getStringCellValue().trim();
            } catch (Exception e) {
                try {
                    return Double.toString(cell.getNumericCellValue());
                } catch (Exception ignore) {
                    return null;
                }
            }
        }
        return null;
    }
}
