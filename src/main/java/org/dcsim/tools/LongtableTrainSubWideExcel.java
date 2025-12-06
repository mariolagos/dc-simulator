package org.dcsim.tools;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.time.ZonedDateTime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

/**
 * Bygger en Excel-fil direkt från longtable.csv:
 *
 * Försöker auto-detektera kolumner:
 *   - tid:       "time_s", "timeSec", "t"
 *   - typ:       "objectType", "objType", "type" (valfri; kan saknas)
 *   - objekt-id: "objectId", "object", "obj", "id"
 *   - signal:    "signal", "signalName", "signalId"
 *   - värde:     "value", "val", "y"
 *
 * Om typ-kolumn saknas används heuristik:
 *   - objectId som börjar med "Train" eller "T" -> Train
 *   - objectId som börjar med "SS" eller "Sub"  -> Sub
 *
 * Skapar två blad om data finns:
 *   - "trains": time_s, <TrainId>.<signal> ...
 *   - "subs":   time_s, <SubId>.<signal> ...
 */
public final class LongtableTrainSubWideExcel {

    public static void main(String[] args) throws Exception {
        String longtablePath = (args.length >= 1)
                ? args[0]
                : "project/3subs2train2/scenario1/longtable.csv";
        String outPath = (args.length >= 2)
                ? args[1]
                : "project/3subs2train2/scenario1/longtable_trains_subs_wide.xlsx";

        Path lt = Paths.get(longtablePath);

        if (!Files.exists(lt)) {
            System.err.println("longtable.csv not found: " + lt.toAbsolutePath());
            System.exit(1);
        }

        System.out.println("[LT-WIDE] Reading longtable: " + lt.toAbsolutePath());
        System.out.println("[LT-WIDE] Output Excel: " + Paths.get(outPath).toAbsolutePath());

        // Sanity/meta-info for wide file
        String invokedAt = ZonedDateTime.now().toString();
        String cliArgs   = String.join(" ", args);

        List<String> lines = Files.readAllLines(lt);
        if (lines.isEmpty()) {
            System.err.println("[LT-WIDE] longtable is empty, aborting.");
            return;
        }

        // --- Header: auto-detektera kolumner ---
        String headerLine = lines.get(0);
        String[] headerCols = splitLine(headerLine);
        int colTime   = indexOf(headerCols, "time_s");
        int colType   = indexOf(headerCols, "object_type");
        int colId     = indexOf(headerCols, "object_id");
        int colSignal = indexOf(headerCols, "signal");
        int colValue  = indexOf(headerCols, "value");

        System.out.printf("[LT-WIDE] Detected columns: time=%s type=%s obj=%s sig=%s val=%s%n",
                headerCols[colTime],
                (colType >= 0 ? headerCols[colType] : "<none>"),
                headerCols[colId],
                headerCols[colSignal],
                headerCols[colValue]);

        final String projectId = System.getProperty("dcsim.project", "dc-simulator");
        final String scenarioId = System.getProperty("dcsim.scenario", "default");
        final String hashTag    = System.getProperty("dcsim.hash", "nohash");

        WideData trains = new WideData();
        WideData subs   = new WideData();

        // --- Data-rader ---
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] f = splitLine(line);
            if (f.length <= Math.max(Math.max(colTime, colId), Math.max(colSignal, colValue))) {
                continue; // för kort rad
            }

            Double t = parseDouble(f[colTime]);
            if (t == null) continue;

            String objId  = safe(f[colId]);
            String signal = safe(f[colSignal]);
            Double val    = parseDouble(f[colValue]);
            if (objId.isEmpty() || signal.isEmpty()) continue;

            String type = (colType >= 0 && colType < f.length) ? safe(f[colType]) : "";

            boolean isTrain = false;
            boolean isSub   = false;

            if (!type.isEmpty()) {
                // explicit typ-kolumn
                if (type.equalsIgnoreCase("Train")) isTrain = true;
                if (type.equalsIgnoreCase("Sub") || type.equalsIgnoreCase("Substation")) isSub = true;
            } else {
                // heuristik baserat på objectId
                if (objId.startsWith("Train") || objId.startsWith("T")) isTrain = true;
                if (objId.startsWith("SS") || objId.toLowerCase().startsWith("sub")) isSub = true;
            }

            if (!isTrain && !isSub) continue;

            if (isTrain) {
                trains.add(t, objId, signal, val);
            } else {
                subs.add(t, objId, signal, val);
            }
        }

        try (Workbook wb = new XSSFWorkbook()) {
            boolean anySheet = false;

            if (!trains.isEmpty()) {
                Sheet sheetTr = wb.createSheet("trains");
                trains.writeToSheet(sheetTr, headerCols[colTime]);
                anySheet = true;
            } else {
                System.out.println("[LT-WIDE] No Train rows found.");
            }

            if (!subs.isEmpty()) {
                Sheet sheetSubs = wb.createSheet("subs");
                subs.writeToSheet(sheetSubs, headerCols[colTime]);
                anySheet = true;
            } else {
                System.out.println("[LT-WIDE] No Sub rows found.");
            }

            if (!anySheet) {
                // skapa åtminstone ett tomt blad så Excel kan öppna filen
                wb.createSheet("empty");
            }

            Sheet signals = wb.createSheet("Signals");

            int r = 0;

            // --- RUN/meta sanity header ---
            Row m0 = signals.createRow(r++);
            m0.createCell(0).setCellValue("generated_at");
            m0.createCell(1).setCellValue(invokedAt);

            Row m1 = signals.createRow(r++);
            m1.createCell(0).setCellValue("longtable_path");
            m1.createCell(1).setCellValue(lt.toAbsolutePath().toString());

            Row m2 = signals.createRow(r++);
            m2.createCell(0).setCellValue("wide_path");
            m2.createCell(1).setCellValue(Paths.get(outPath).toAbsolutePath().toString());

            Row m3 = signals.createRow(r++);
            m3.createCell(0).setCellValue("cli_args");
            m3.createCell(1).setCellValue(cliArgs);

            // tom rad mellan meta och signal-tabellen
            r++;

            Row header = signals.createRow(r++);
            header.createCell(0).setCellValue("Signal");
            header.createCell(1).setCellValue("Description");

            signals.createRow(r++).createCell(0).setCellValue("time_s");
            signals.getRow(r-1).createCell(1).setCellValue("Simulation time [s]");

            // tom rad mellan före trains-signals
            r++;
            signals.createRow(r++).createCell(0).setCellValue("trains:");

            signals.createRow(r++).createCell(0).setCellValue("P_brk_net_W");
            signals.getRow(r-1).createCell(1).setCellValue("Part of braking power actually sent to the DC network (regeneration, negative).");

            signals.createRow(r++).createCell(0).setCellValue("P_brk_req_W");
            signals.getRow(r-1).createCell(1).setCellValue("Requested braking power (negative during braking).");

            signals.createRow(r++).createCell(0).setCellValue("P_brk_res_W");
            signals.getRow(r-1).createCell(1).setCellValue("Part of braking power dissipated in onboard resistors (negative).");

            signals.createRow(r++).createCell(0).setCellValue("P_net_W");
            signals.getRow(r-1).createCell(1).setCellValue("Net electrical power wrt DC network. >0: network → object, <0: object → network.");

            signals.createRow(r++).createCell(0).setCellValue("V_V");
            signals.getRow(r-1).createCell(1).setCellValue("DC voltage at the object’s connection node [V].");

            signals.createRow(r++).createCell(0).setCellValue("aux_W");
            signals.getRow(r-1).createCell(1).setCellValue("Auxiliary power demand (HVAC, compressors, etc).");

            signals.createRow(r++).createCell(0).setCellValue("brk_W");
            signals.getRow(r-1).createCell(1).setCellValue("Total braking power request [W] with sign: negative when braking (train → mechanical/electrical), 0 otherwise.");

            signals.createRow(r++).createCell(0).setCellValue("brk_prof_W");
            signals.getRow(r-1).createCell(1).setCellValue("Braking power from the driving/braking profile [kW]. Positive number ≈ braking magnitude; used for reference only.");

            signals.createRow(r++).createCell(0).setCellValue("mot_W");
            signals.getRow(r-1).createCell(1).setCellValue("Traction motor power request (positive when motoring).");

            signals.createRow(r++).createCell(0).setCellValue("pos_m");
            signals.getRow(r-1).createCell(1).setCellValue("Traction motor power request (positive when motoring).");

            signals.createRow(r++).createCell(0).setCellValue("req_W");
            signals.getRow(r-1).createCell(1).setCellValue("Traction motor power request (positive when motoring).");

            signals.createRow(r++).createCell(0).setCellValue("req_net_W");
            signals.getRow(r-1).createCell(1).setCellValue("Requested net electrical power wrt the DC network [W]: motoring + auxiliaries + regenerative braking component. >0: network → train, <0: train → network.");

            signals.createRow(r++).createCell(0).setCellValue("req_trac_W");
            signals.getRow(r-1).createCell(1).setCellValue("Traction-related” requested power [W]: motoring − braking + auxiliaries (signed). Gives an idea of \u200B\u200Bwhat the profile “wants” to do before the network limits anything.");

            signals.createRow(r++).createCell(0).setCellValue("speed_mps");
            signals.getRow(r-1).createCell(1).setCellValue("Train speed [m/s].");

            signals.createRow(r++).createCell(0).setCellValue("V_V");
            signals.getRow(r-1).createCell(1).setCellValue("DC voltage at the train connection node [V].");
            signals.createRow(r++).createCell(0).setCellValue("I_A");
            signals.getRow(r-1).createCell(1).setCellValue("Current into the DC network [A]. >0: nät → tåg, <0: tåg → nät.");

            // tom rad mellan före subs-signals
            r++;
            signals.createRow(r++).createCell(0).setCellValue("subs:");

            signals.createRow(r++).createCell(0).setCellValue("V_V");
            signals.getRow(r-1).createCell(1).setCellValue("DC bus voltage at the substation DC terminal [V].");

            signals.createRow(r++).createCell(0).setCellValue("I_A");
            signals.getRow(r-1).createCell(1).setCellValue("DC current at the substation terminal [A]. >0: substation → mains, <0: mains → substation (backfeed).");

            signals.createRow(r++).createCell(0).setCellValue("P_net_W");
            signals.getRow(r-1).createCell(1).setCellValue("Net power into the DC network at the substation terminal [W]." +
                    " >0: substation delivers power to the DC network. <0: substation absorbs power from the DC network (regenerative braking energy).");

            signals.createRow(r++).createCell(0).setCellValue("P_Loss_W");
            signals.getRow(r-1).createCell(1).setCellValue("Internal losses in the substation converter/transformer [W], calculated approximately as V_bus^2/R_int. Always >= =.");

            try (OutputStream out = Files.newOutputStream(Paths.get(outPath))) {
                wb.write(out);
            }
        }

        System.out.println("[LT-WIDE] Done.");
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equals(header[i].trim())) {
                return i;
            }
        }
        throw new IllegalStateException("[LT-WIDE] Column not found: " + name);
    }

    // ==== Hjälpklass för wide-data ==== //
    private static final class WideData {
        private final TreeSet<Double> times = new TreeSet<>();
        private final TreeSet<String> ids = new TreeSet<>();
        private final TreeSet<String> signals = new TreeSet<>();
        private final Map<String, Double> values = new HashMap<>();

        void add(Double t, String id, String signal, Double value) {
            times.add(t);
            ids.add(id);
            signals.add(signal);
            if (value != null) {
                values.put(key(t, id, signal), value);
            }
        }

        boolean isEmpty() {
            return times.isEmpty();
        }

        void writeToSheet(Sheet sheet, String timeHeader) {
            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            int col = 0;
            header.createCell(col++).setCellValue(timeHeader);

            List<String> idList = new ArrayList<>(ids);
            List<String> sigList = new ArrayList<>(signals);

            for (String id : idList) {
                for (String sName : sigList) {
                    String h = id + "." + sName;
                    header.createCell(col++).setCellValue(h);
                }
            }

            for (Double t : times) {
                Row r = sheet.createRow(rowIdx++);
                int c = 0;
                r.createCell(c++).setCellValue(t);

                for (String id : idList) {
                    for (String sName : sigList) {
                        Double v = values.get(key(t, id, sName));
                        if (v != null) {
                            r.createCell(c++).setCellValue(v);
                        } else {
                            c++;
                        }
                    }
                }
            }

            for (int i = 0; i < Math.min(8, header.getLastCellNum()); i++) {
                sheet.autoSizeColumn(i);
            }
        }

        private String key(Double t, String id, String signal) {
            return t + "|" + id + "|" + signal;
        }
    }

    // ==== Små hjälpare ==== //

    private static String[] splitLine(String line) {
        // stöd för komma, semikolon och tab
        return line.split("[,;\t]");
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static Double parseDouble(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int findIndex(String[] cols, String... candidates) {
        for (int i = 0; i < cols.length; i++) {
            String c = cols[i].trim();
            for (String cand : candidates) {
                if (c.equalsIgnoreCase(cand)) return i;
            }
        }
        return -1;
    }
}
