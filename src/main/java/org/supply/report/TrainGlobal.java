package org.supply.report;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supply.domain.RunCsvInput;
import org.supply.domain.RunSource;
import org.supply.domain.RunSourceType;
import org.supply.loader.RunCsvInputFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Global train report with deterministic stitching of complete RunExcel legs.
 *
 * All times are seconds from midnight. RunCsvInputFactory is the single source
 * of truth for expanded train IDs, template ordering and synchronized departures.
 */
public final class TrainGlobal {
    private static final double J_PER_KWH = 3_600_000.0;
    // Allows a final integration sample just before the scheduled arrival.
    private static final double ARRIVAL_TIME_TOLERANCE_S = 0.25;
    private static final String[] HEADERS = {
            "StudyId", "Calc Type", "train_id", "route_id", "leg",
            "from_station", "to_station", "start_time", "end_time", "duration_s",
            "e_consumed_kWh", "e_regenerated_kWh", "e_net_kWh",
            "p_mean_kW", "p_min_kW", "p_max_kW",
            "i_mean_A", "i_rms_A", "i_min_A", "i_max_A",
            "u_mean_V", "u_min_V", "u_max_V"
    };

    private TrainGlobal() {}

    /** Command-line entry point for an explicit, reproducible Global Train run. */
    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            throw new IllegalArgumentException(
                    "Usage: TrainGlobal <study.conf> <results_train.xlsx> "
                            + "<train_global.xlsx> <StudyId> <CalcType> <train_id> [train_id ...]");
        }
        Path confFile = Path.of(args[0]).toAbsolutePath().normalize();
        Config config = ConfigFactory.parseFile(confFile.toFile()).resolve();
        Config dcsim = config.getConfig("dcsim");
        List<String> trainIds = Arrays.stream(args)
                .skip(5)
                .flatMap(arg -> Arrays.stream(arg.split(",")))
                .map(String::trim)
                .toList();

        write(Path.of(args[1]), Path.of(args[2]), dcsim, confFile,
                args[3], args[4], trainIds);
    }

    /**
     * @param selectedTrainIds exact expanded IDs, e.g. we-015,ew-015.
     */
    public static void write(Path resultsTrain, Path output, Config dcsim, Path confFile,
                             String studyId, String calcType, List<String> selectedTrainIds)
            throws Exception {
        if (studyId == null || studyId.isBlank() || calcType == null || calcType.isBlank())
            throw new IllegalArgumentException("StudyId and Calc Type must not be blank");
        if (selectedTrainIds == null || selectedTrainIds.isEmpty()
                || selectedTrainIds.stream().anyMatch(s -> s == null || s.isBlank())
                || new HashSet<>(selectedTrainIds).size() != selectedTrainIds.size())
            throw new IllegalArgumentException("Specify distinct, nonblank train IDs");

        RunCsvInput input = new RunCsvInputFactory().build(dcsim, confFile);
        Map<String, List<Leg>> legsByTrain = collectLegs(input, new HashSet<>(input.trainIds()));

        try (Workbook source = WorkbookFactory.create(resultsTrain.toFile());
             Workbook target = new XSSFWorkbook()) {
            Sheet summary = target.createSheet("Train Global");
            Row header = summary.createRow(0);
            for (int c = 0; c < HEADERS.length; c++)
                header.createCell(c).setCellValue(HEADERS[c]);

            CellStyle timeStyle = target.createCellStyle();
            timeStyle.setDataFormat(target.createDataFormat().getFormat("[h]:mm:ss"));
            CellStyle numberStyle = target.createCellStyle();
            numberStyle.setDataFormat(target.createDataFormat().getFormat("0.000"));

            Map<String, List<Sample>> samplesByTrain = new HashMap<>();
            for (String trainId : legsByTrain.keySet()) {
                if (source.getSheet(trainId) != null)
                    samplesByTrain.put(trainId, readSamples(source, trainId));
            }
            int rowIndex = copyExistingRows(output, summary, studyId, calcType, timeStyle, numberStyle);
            for (String requestedId : selectedTrainIds) {
                List<ChosenLeg> journey = selectJourney(requestedId, legsByTrain, samplesByTrain);
                for (ChosenLeg chosen : journey)
                    writeLeg(summary.createRow(rowIndex++), studyId, calcType, chosen.trainId,
                            chosen.leg, chosen.index + 1, samplesByTrain.get(chosen.trainId),
                            timeStyle, numberStyle);
            }
            summary.createFreezePane(3, 1);
            for (int c = 0; c < HEADERS.length; c++)
                summary.setColumnWidth(c, (c < 7 ? 18 : 19) * 256);
            copyMetadata(source, target);
            updateSources(output, source, target, studyId, calcType);
            Sheet method = target.createSheet("Method");
            String[] notes = {
                    "Source: results_train.xlsx and RunExcel timetable sheets.",
                    "Journey selection: requested train IDs identify traffic patterns; the earliest complete departure is selected.",
                    "Leg ordering and synchronized departure times: RunCsvInputFactory.",
                    "Leg departure and arrival are read from timetable (Time s, Type).",
                    "Energy: interpolated cumulative counter at arrival minus departure.",
                    "Linear interpolation is used for boundaries between samples.",
                    "For time-weighted statistics, each right-endpoint sample represents the preceding interval.",
                    "Boundary values are linearly interpolated; min/max include boundaries and interior samples.",
                    "p_mean_kW is derived from net energy divided by leg duration.",
                    "Only complete legs are used; subsequent legs may come from other trains of the same station sequence.",
                    "A train is retained as long as it has complete consecutive legs; replacement uses earliest complete departure.",
                    "Energy regenerated retains the source counter's sign convention.",
                    "StudyId and Calc Type are user-supplied; metadata is copied from the latest source.",
                    "Existing rows for the same StudyId and Calc Type are replaced; other cases are preserved.",
                    "Sources records provenance for each retained case."
            };
            for (int i = 0; i < notes.length; i++)
                method.createRow(i).createCell(0).setCellValue(notes[i]);
            method.setColumnWidth(0, 115 * 256);
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "train-global-", ".xlsx");
            try {
                try (var stream = Files.newOutputStream(temp)) { target.write(stream); }
                try {
                    Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    // Rebuild rather than editing the old workbook in place: no stale rows remain.
    static int copyExistingRows(Path destination, Sheet target, String studyId, String calcType,
                                CellStyle timeStyle, CellStyle numberStyle) throws IOException {
        if (!Files.exists(destination)) return 1;
        try (var stream = Files.newInputStream(destination);
             Workbook previous = WorkbookFactory.create(stream)) {
            Sheet old = previous.getSheet("Train Global");
            if (old == null) throw new IllegalArgumentException("Missing Train Global sheet: " + destination);
            Row header = old.getRow(0);
            if (header == null) throw new IllegalArgumentException("Missing Train Global header");
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.getCell(c);
                if (cell == null || cell.getCellType() != CellType.STRING
                        || !HEADERS[c].equals(cell.getStringCellValue()))
                    throw new IllegalArgumentException("Incompatible Train Global header at column " + c);
            }
            int next = 1;
            for (int r = 1; r <= old.getLastRowNum(); r++) {
                Row src = old.getRow(r);
                if (src == null) continue;
                if (studyId.equals(textCell(src, 0)) && calcType.equals(textCell(src, 1))) continue;
                Row dst = target.createRow(next++);
                for (int c = 0; c < HEADERS.length; c++) {
                    Cell from = src.getCell(c);
                    if (from == null) continue;
                    Cell to = dst.createCell(c);
                    switch (from.getCellType()) {
                        case STRING -> to.setCellValue(from.getStringCellValue());
                        case NUMERIC -> to.setCellValue(from.getNumericCellValue());
                        case BOOLEAN -> to.setCellValue(from.getBooleanCellValue());
                        case BLANK -> { }
                        default -> throw new IllegalArgumentException("Unsupported Train Global cell type at row " + r);
                    }
                    if (c == 7 || c == 8) to.setCellStyle(timeStyle);
                    else if (from.getCellType() == CellType.NUMERIC) to.setCellStyle(numberStyle);
                }
            }
            return next;
        }
    }

    private static String textCell(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() != CellType.STRING)
            throw new IllegalArgumentException("Expected text in Train Global column " + column);
        return cell.getStringCellValue();
    }

    static void updateSources(Path destination, Workbook input, Workbook output,
                              String studyId, String calcType) throws IOException {
        String[] columns = {"StudyId", "Calc Type", "project", "scenario", "base_hash", "generated_at"};
        Sheet sources = output.createSheet("Sources");
        Row header = sources.createRow(0);
        for (int c = 0; c < columns.length; c++) header.createCell(c).setCellValue(columns[c]);
        int next = 1;
        if (Files.exists(destination)) {
            try (var stream = Files.newInputStream(destination);
                 Workbook previous = WorkbookFactory.create(stream)) {
                Sheet old = previous.getSheet("Sources");
                if (old != null) {
                    Row oldHeader = old.getRow(0);
                    for (int c = 0; c < columns.length; c++) {
                        Cell cell = oldHeader == null ? null : oldHeader.getCell(c);
                        if (cell == null || cell.getCellType() != CellType.STRING
                                || !columns[c].equals(cell.getStringCellValue()))
                            throw new IllegalArgumentException("Incompatible Sources header at column " + c);
                    }
                    for (int r = 1; r <= old.getLastRowNum(); r++) {
                        Row src = old.getRow(r);
                        if (src == null) continue;
                        if (studyId.equals(textCell(src, 0)) && calcType.equals(textCell(src, 1))) continue;
                        Row dst = sources.createRow(next++);
                        for (int c = 0; c < columns.length; c++)
                            dst.createCell(c).setCellValue(textCell(src, c));
                    }
                }
            }
        }
        Sheet metadata = input.getSheet("Metadata");
        if (metadata == null) throw new IllegalArgumentException("Missing Metadata sheet");
        Map<String, String> values = new HashMap<>();
        for (Row row : metadata) {
            Cell key = row.getCell(0), value = row.getCell(1);
            if (key != null && value != null && key.getCellType() == CellType.STRING
                    && value.getCellType() == CellType.STRING)
                values.put(key.getStringCellValue(), value.getStringCellValue());
        }
        Row dst = sources.createRow(next);
        dst.createCell(0).setCellValue(studyId);
        dst.createCell(1).setCellValue(calcType);
        for (int c = 2; c < columns.length; c++)
            dst.createCell(c).setCellValue(values.getOrDefault(columns[c], ""));
        for (int c = 0; c < columns.length; c++) sources.setColumnWidth(c, 24 * 256);
    }

    /** One original leg; index is zero-based within its source train. */
    record ChosenLeg(String trainId, int index, Leg leg) {}

    /**
     * The requested ID identifies the journey pattern, not necessarily the first
     * physical train. Candidate trains must have the same ordered station sequence.
     * Prefer the earliest journey departure covered by the result window, then
     * retain each train until its next leg is incomplete.
     */
    static List<ChosenLeg> selectJourney(String requestedId,
                                         Map<String, List<Leg>> legsByTrain,
                                         Map<String, List<Sample>> samplesByTrain) {
        List<Leg> pattern = legsByTrain.get(requestedId);
        if (pattern == null || pattern.isEmpty())
            throw new IllegalArgumentException("Train not found in traffic plan: " + requestedId);
        List<String> candidates = legsByTrain.keySet().stream()
                .filter(id -> sameJourney(pattern, legsByTrain.get(id)))
                .filter(samplesByTrain::containsKey)
                .toList();
        if (candidates.isEmpty())
            throw new IllegalArgumentException("No result trains for journey: " + requestedId);

        // First leg must be a real journey departure within the available window.
        String current = candidates.stream()
                .filter(id -> covered(legsByTrain.get(id).get(0), samplesByTrain.get(id)))
                .min(Comparator.comparingDouble((String id) -> legsByTrain.get(id).get(0).startS)
                        .thenComparing(id -> id))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No complete journey departure in results for: " + requestedId));

        List<ChosenLeg> result = new ArrayList<>();
        int index = 0;
        while (index < pattern.size()) {
            List<Leg> legs = legsByTrain.get(current);
            List<Sample> samples = samplesByTrain.get(current);
            while (index < pattern.size() && covered(legs.get(index), samples)) {
                result.add(new ChosenLeg(current, index, legs.get(index)));
                index++;
            }
            if (index == pattern.size()) break;
            final int next = index;
            final String previous = current;
            current = candidates.stream()
                    .filter(id -> !id.equals(previous))
                    .filter(id -> covered(legsByTrain.get(id).get(next), samplesByTrain.get(id)))
                    .min(Comparator.comparingDouble((String id) -> legsByTrain.get(id).get(next).startS)
                            .thenComparing(id -> id))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No complete continuation from station " + pattern.get(next).from
                                    + " (leg " + (next + 1) + ") for " + requestedId));
        }
        return result;
    }

    private static boolean sameJourney(List<Leg> a, List<Leg> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).from.equals(b.get(i).from)
                    || !a.get(i).to.equals(b.get(i).to)
                    || !a.get(i).route.equals(b.get(i).route)) return false;
        }
        return true;
    }

    private static boolean covered(Leg leg, List<Sample> samples) {
        return samples != null && samples.size() >= 2
                && leg.startS >= samples.get(0).t
                && (leg.endS <= samples.get(samples.size() - 1).t
                || (leg.endS > samples.get(samples.size() - 1).t
                && leg.endS - samples.get(samples.size() - 1).t <= ARRIVAL_TIME_TOLERANCE_S));
    }

    private static Map<String, List<Leg>> collectLegs(RunCsvInput input, Set<String> selected)
            throws Exception {
        Map<String, List<Leg>> result = new LinkedHashMap<>();
        Map<String, Double> firstSourceDeparture = new HashMap<>();
        for (int i = 0; i < input.trainIds().size(); i++) {
            String id = input.trainIds().get(i);
            if (!selected.contains(id)) continue;
            RunSource runSource = input.runSources().get(i);
            if (runSource.type() != RunSourceType.EXCEL)
                throw new IllegalArgumentException("Global Train v1 requires RunExcel: " + id);
            Timetable legTimes = readTimetable(runSource.file());
            double first = firstSourceDeparture.computeIfAbsent(id, k -> legTimes.sourceDepartureS);
            Integer override = input.relativeLegDepartureTimes().get(i);
            double[] bounds = legBounds(input.departureTimes().get(i), first,
                    legTimes.sourceDepartureS, legTimes.departureS, legTimes.arrivalS, override);

            double start = bounds[0];

            double[] profileBounds = readProfileTimeBounds(
                    runSource.file(),
                    runSource.sheet()
            );

            double end = start + profileBounds[1] - profileBounds[0];
            if (!(end > start)) throw new IllegalArgumentException("Invalid leg duration: " + runSource.file());
            result.computeIfAbsent(id, k -> new ArrayList<>())
                    .add(new Leg(input.routeIds().get(i), legTimes.from, legTimes.to, start, end));
        }
        return result;
    }

    // The Time column is relative to the whole source journey; Time s is leg-local.
    // A configured leg departure overrides the source-relative departure offset.
    static double[] legBounds(double trainDepartureS, double firstSourceDepartureS,
                              double sourceDepartureS, double legDepartureS,
                              double legArrivalS, Integer relativeLegDepartureS) {
        double relative = relativeLegDepartureS != null
                ? relativeLegDepartureS : sourceDepartureS - firstSourceDepartureS;
        double start = trainDepartureS + relative;
        return new double[]{start, start + legArrivalS - legDepartureS};
    }

    private static Timetable readTimetable(Path file) throws IOException {
        try (var stream = Files.newInputStream(file);
             Workbook book = WorkbookFactory.create(stream)) {
            Sheet sheet = book.getSheet("timetable");
            if (sheet == null) throw new IllegalArgumentException("Missing timetable: " + file);
            Row header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Empty timetable: " + file);
            Map<String, Integer> cols = new HashMap<>();
            for (Cell cell : header)
                cols.put(cell.toString().trim().toLowerCase(Locale.ROOT), cell.getColumnIndex());
            for (String field : List.of("station", "time", "time s", "type"))
                if (!cols.containsKey(field))
                    throw new IllegalArgumentException("Missing timetable column " + field + ": " + file);
            String from = null, to = null;
            Double departure = null, arrival = null, sourceDeparture = null;
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String type = formatted(row, cols.get("type"), formatter).trim().toLowerCase(Locale.ROOT);
                if (!type.equals("departure") && !type.equals("arrival")) continue;
                Cell time = row.getCell(cols.get("time s"));
                if (time == null || time.getCellType() != CellType.NUMERIC)
                    throw new IllegalArgumentException("Non-numeric timetable Time s: " + file + " row " + (r+1));
                if (type.equals("departure")) {
                    if (departure != null) throw new IllegalArgumentException("Multiple departures: " + file);
                    departure = time.getNumericCellValue();
                    sourceDeparture = timetableTimeSeconds(row.getCell(cols.get("time")), formatter);
                    from = formatted(row, cols.get("station"), formatter);
                } else {
                    if (arrival != null) throw new IllegalArgumentException("Multiple arrivals: " + file);
                    arrival = time.getNumericCellValue();
                    to = formatted(row, cols.get("station"), formatter);
                }
            }
            if (departure == null || arrival == null || arrival <= departure)
                throw new IllegalArgumentException("Invalid departure/arrival timetable: " + file);
            return new Timetable(from, to, departure, arrival, sourceDeparture);
        }
    }


    private static double[] readProfileTimeBounds(Path file, String sheetName)
            throws IOException {
        try (var stream = Files.newInputStream(file);
             Workbook book = WorkbookFactory.create(stream)) {

            Sheet sheet = book.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Missing RunExcel sheet '" + sheetName + "': " + file);
            }

            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException(
                        "Empty RunExcel sheet: " + file);
            }

            Integer timeColumn = null;
            for (Cell cell : header) {
                if ("time [s]".equals(cell.toString().trim())) {
                    timeColumn = cell.getColumnIndex();
                    break;
                }
            }

            if (timeColumn == null) {
                throw new IllegalArgumentException(
                        "Missing column 'time [s]': " + file);
            }

            Double first = null;
            Double last = null;

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Cell cell = row.getCell(timeColumn);
                if (cell == null || cell.getCellType() != CellType.NUMERIC) {
                    continue;
                }

                double time = cell.getNumericCellValue();
                if (!Double.isFinite(time)) {
                    throw new IllegalArgumentException(
                            "Invalid profile time: " + file + " row " + (r + 1));
                }

                if (last != null && time <= last) {
                    throw new IllegalArgumentException(
                            "Non-increasing profile time: " + file
                                    + " row " + (r + 1));
                }

                if (first == null) first = time;
                last = time;
            }

            if (first == null || last == null || last <= first) {
                throw new IllegalArgumentException(
                        "Invalid RunExcel profile duration: " + file);
            }

            return new double[]{first, last};
        }
    }

    // Match RunCsvFromExcel.readRelativeDepartureS: Excel time serial or HH:mm:ss text.
    private static double timetableTimeSeconds(Cell cell, DataFormatter formatter) {
        if (cell == null) throw new IllegalArgumentException("Missing timetable departure Time");
        if (cell.getCellType() == CellType.NUMERIC)
            return cell.getNumericCellValue() * 86400.0;
        String value = formatter.formatCellValue(cell).trim();
        String[] parts = value.split(":", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid timetable Time: " + value);
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            double sec = Double.parseDouble(parts[2].replace(',', '.'));
            if (h < 0 || m < 0 || m >= 60 || sec < 0 || sec >= 60)
                throw new IllegalArgumentException("Invalid timetable Time: " + value);
            return h * 3600.0 + m * 60.0 + sec;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid timetable Time: " + value, ex);
        }
    }

    private static String formatted(Row row, int column, DataFormatter formatter) {
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private static List<Sample> readSamples(Workbook book, String id) {
        Sheet sheet = book.getSheet(id);
        if (sheet == null) throw new IllegalArgumentException("Missing results sheet for " + id);
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = new HashMap<>();
        for (Cell cell : header) {
            if (cell.getCellType() != CellType.STRING) continue;
            String name = cell.getStringCellValue();
            if (name.startsWith(id + ".")) cols.put(name.substring(id.length() + 1), cell.getColumnIndex());
        }
        for (String field : List.of("time_s", "u_V", "i_A", "p_W",
                "e_consumed_J", "e_regenerated_J", "e_net_J"))
            if (!cols.containsKey(field)) throw new IllegalArgumentException("Missing " + id + "." + field);
        List<Sample> samples = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell time = row.getCell(cols.get("time_s"));
            if (time == null || time.getCellType() == CellType.BLANK) continue;
            Sample s = new Sample(num(row, cols, "time_s"), num(row, cols, "u_V"),
                    num(row, cols, "i_A"), num(row, cols, "p_W"),
                    num(row, cols, "e_consumed_J"), num(row, cols, "e_regenerated_J"),
                    num(row, cols, "e_net_J"));
            if (!samples.isEmpty() && s.t <= samples.get(samples.size() - 1).t)
                throw new IllegalArgumentException("Non-increasing time for " + id + " at row " + (r+1));
            samples.add(s);
        }
        if (samples.size() < 2) throw new IllegalArgumentException("Insufficient samples: " + id);
        return samples;
    }

    private static double num(Row row, Map<String, Integer> cols, String field) {
        Cell cell = row.getCell(cols.get(field));
        if (cell == null || cell.getCellType() != CellType.NUMERIC)
            throw new IllegalArgumentException("Expected numeric " + field + " at row " + (row.getRowNum()+1));
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + field);
        return value;
    }

    private static Sample at(List<Sample> samples, double t) {
        int lo = 0, hi = samples.size() - 1;
        if (t < samples.get(lo).t || t > samples.get(hi).t)
            throw new IllegalArgumentException("Time outside available results: " + t);
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (samples.get(mid).t < t) lo = mid + 1;
            else hi = mid - 1;
        }
        if (lo < samples.size() && samples.get(lo).t == t) return samples.get(lo);
        Sample a = samples.get(lo - 1), b = samples.get(lo);
        double f = (t - a.t) / (b.t - a.t);
        return new Sample(t, mix(a.u,b.u,f), mix(a.i,b.i,f), mix(a.p,b.p,f),
                mix(a.consumed,b.consumed,f), mix(a.regenerated,b.regenerated,f),
                mix(a.net,b.net,f));
    }

    private static double mix(double a, double b, double f) { return a + (b-a)*f; }

    private static void writeLeg(Row row, String study, String calc, String train,
                                 Leg leg, int index, List<Sample> all,
                                 CellStyle timeStyle, CellStyle numberStyle) {
        List<Sample> points = new ArrayList<>();
        points.add(at(all, leg.startS));
        for (Sample sample : all)
            if (sample.t > leg.startS && sample.t < leg.endS) points.add(sample);
        double actualEndS = Math.min(leg.endS, all.get(all.size() - 1).t);
        points.add(at(all, actualEndS));
        double duration = actualEndS - leg.startS;
        double sumI = 0, sumI2 = 0, sumU = 0;
        double minI = Double.POSITIVE_INFINITY, maxI = Double.NEGATIVE_INFINITY;
        double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
        double minP = Double.POSITIVE_INFINITY, maxP = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < points.size(); k++) {
            Sample s = points.get(k);
            minI = Math.min(minI, s.i); maxI = Math.max(maxI, s.i);
            minU = Math.min(minU, s.u); maxU = Math.max(maxU, s.u);
            minP = Math.min(minP, s.p); maxP = Math.max(maxP, s.p);
            if (k == 0) continue;
            double dt = s.t - points.get(k-1).t;
            sumI += s.i * dt; sumI2 += s.i*s.i*dt; sumU += s.u*dt;
        }
        double consumed = (points.get(points.size()-1).consumed - points.get(0).consumed)/J_PER_KWH;
        double regenerated = (points.get(points.size()-1).regenerated - points.get(0).regenerated)/J_PER_KWH;
        double net = (points.get(points.size()-1).net - points.get(0).net)/J_PER_KWH;
        string(row,0,study); string(row,1,calc); string(row,2,train);
        string(row,3,leg.route); numeric(row,4,index,numberStyle);
        string(row,5,leg.from); string(row,6,leg.to);
        numeric(row,7,leg.startS/86400.0,timeStyle); numeric(row,8,actualEndS/86400.0,timeStyle);
        numeric(row,9,duration,numberStyle);
        double[] values = {consumed,regenerated,net,net*3600.0/duration,minP/1000.0,maxP/1000.0,
                sumI/duration,Math.sqrt(sumI2/duration),minI,maxI,sumU/duration,minU,maxU};
        for (int c = 0; c < values.length; c++) numeric(row,10+c,values[c],numberStyle);
    }

    private static void numeric(Row row,int col,double v,CellStyle style) {
        Cell cell = row.createCell(col); cell.setCellValue(v); cell.setCellStyle(style);
    }
    private static void string(Row row,int col,String v) { row.createCell(col).setCellValue(v); }

    private static void copyMetadata(Workbook source, Workbook target) {
        Sheet original = source.getSheet("Metadata");
        if (original == null) throw new IllegalArgumentException("Missing Metadata sheet");
        Sheet copy = target.createSheet("Metadata");
        int r = 0;
        for (Row row : original) {
            Row dst = copy.createRow(r++);
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING)
                    dst.createCell(cell.getColumnIndex()).setCellValue(cell.getStringCellValue());
                else if (cell.getCellType() == CellType.NUMERIC)
                    dst.createCell(cell.getColumnIndex()).setCellValue(cell.getNumericCellValue());
            }
        }
        copy.autoSizeColumn(0); copy.autoSizeColumn(1);
    }

    private record Timetable(String from,String to,double departureS,double arrivalS,double sourceDepartureS) {}
    record Leg(String route,String from,String to,double startS,double endS) {}
    record Sample(double t,double u,double i,double p,
                  double consumed,double regenerated,double net) {}
}
