package org.supply.report;

import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.*;
import org.supply.loader.RunCsvInputFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Adds DC energy differences per RunExcel leg using the simulator longtable. */
public final class TrainLegGlobal {
    private static final String CONSUMED = "consumed energy [kWh]";
    private static final String REGENERATED = "regenerated energy [kWh]";
    private static final String DIFF_CONSUMED = "diff " + CONSUMED;
    private static final String DIFF_REGENERATED = "diff " + REGENERATED;
    private static final double POSITION_TOLERANCE_M = 2.0;
    private static final double DURATION_TOLERANCE_S = 2.0;
    private static final double J_PER_KWH = 3_600_000.0;

    private TrainLegGlobal() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 7) {
            throw new IllegalArgumentException(
                    "Usage: TrainLegGlobal <global.xlsx> <longtable.csv> <output.xlsx>"
                            + " <calcType> [sampleInterval_s; default 1] [--study-conf <path>]");
        }
        int index = 4;
        double sampleInterval = 1.0;
        if (index < args.length && !"--study-conf".equals(args[index])) {
            sampleInterval = Double.parseDouble(args[index++]);
        }
        Map<String, List<TrainGlobal.Leg>> plan = null;
        if (index < args.length) {
            if (args.length - index != 2 || !"--study-conf".equals(args[index])) {
                throw new IllegalArgumentException("Expected --study-conf <path>");
            }
            Path conf = Path.of(args[index + 1]).toAbsolutePath().normalize();
            var config = ConfigFactory.parseFile(conf.toFile()).resolve().getConfig("dcsim");
            var input = new RunCsvInputFactory().build(config, conf);
            plan = TrainGlobal.collectLegs(input, new HashSet<>(input.trainIds()));
        }
        write(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), args[3], sampleInterval, plan);
    }

    public static void write(Path source, Path longtable, Path output, String calcType,
                             double sampleInterval) throws IOException {
        write(source, longtable, output, calcType, sampleInterval, null);
    }

    private static void write(Path source, Path longtable, Path output, String calcType,
                              double sampleInterval, Map<String, List<TrainGlobal.Leg>> plan)
            throws IOException {
        if (!Double.isFinite(sampleInterval) || sampleInterval <= 0 || calcType.isBlank()) {
            throw new IllegalArgumentException("Supply a calculation type and positive sample interval");
        }
        Path destination = output.toAbsolutePath().normalize();
        if (source.toAbsolutePath().normalize().equals(destination)
                || longtable.toAbsolutePath().normalize().equals(destination)
                || (Files.exists(destination) && (Files.isSameFile(source, destination)
                || Files.isSameFile(longtable, destination)))) {
            throw new IllegalArgumentException("Use a separate output workbook");
        }

        Map<String, NavigableMap<Double, Tick>> ticks = readTrainTicks(longtable);
        List<Passage> passages = new ArrayList<>();
        if (plan == null) {
            ticks.forEach((train, samples) -> collectPassages(train, samples, sampleInterval, passages));
        }
        boolean existing = Files.exists(destination);
        try (var original = readWorkbook(source);
             var result = readWorkbook(existing ? destination : source)) {
            Sheet sourceSheet = summaries(original);
            Sheet sheet = summaries(result);
            if (existing) {
                verifySource(sourceSheet, sheet);
            } else {
                insertColumn(sheet, CONSUMED, DIFF_CONSUMED);
                insertColumn(sheet, REGENERATED, DIFF_REGENERATED);
            }
            int updated = update(sheet, calcType, passages, plan, ticks, sampleInterval);
            if (updated == 0) {
                throw new IllegalArgumentException("No complete matching legs for " + calcType);
            }
            save(result, destination);
            System.out.printf("Updated %d leg(s) for %s%n", updated, calcType);
        }
    }

    private static Workbook readWorkbook(Path path) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            return new XSSFWorkbook(stream);
        }
    }

    private static Sheet summaries(Workbook book) {
        Sheet sheet = book.getSheet("Summaries");
        if (sheet == null || sheet.getRow(0) == null) {
            throw new IllegalArgumentException("Missing Summaries sheet or header");
        }
        return sheet;
    }

    private static int column(Row header, String name) {
        for (Cell cell : header) {
            if (name.equalsIgnoreCase(cell.toString().strip())) {
                return cell.getColumnIndex();
            }
        }
        throw new IllegalArgumentException("Missing column: " + name);
    }

    private static void insertColumn(Sheet sheet, String after, String name) {
        Row header = sheet.getRow(0);
        int index = column(header, after) + 1;
        int last = header.getLastCellNum() - 1;
        CellCopyPolicy policy = new CellCopyPolicy();
        for (Row row : sheet) {
            for (int col = row.getLastCellNum() - 1; col >= index; col--) {
                Cell old = row.getCell(col);
                if (old == null) continue;
                XSSFCell shifted = (XSSFCell) row.createCell(col + 1);
                shifted.copyCellFrom(old, policy);
                row.removeCell(old);
            }
        }
        for (int col = last; col >= index; col--) {
            sheet.setColumnWidth(col + 1, sheet.getColumnWidth(col));
        }
        header.createCell(index).setCellValue(name);
        sheet.setColumnWidth(index, (name.length() + 4) * 256);
    }

    // Refuse to attach previously saved values to a different source row.
    private static void verifySource(Sheet source, Sheet target) {
        Row header = source.getRow(0);
        Row out = target.getRow(0);
        int consumed = column(out, DIFF_CONSUMED);
        int regenerated = column(out, DIFF_REGENERATED);
        if (source.getLastRowNum() != target.getLastRowNum()
                || out.getLastCellNum() != header.getLastCellNum() + 2) {
            throw new IllegalArgumentException("Output workbook does not match the RunExcel source");
        }
        for (int row = 0; row <= source.getLastRowNum(); row++) {
            int inputColumn = 0;
            for (int col = 0; col < out.getLastCellNum(); col++) {
                if (col == consumed || col == regenerated) continue;
                if (!value(source.getRow(row), inputColumn++).equals(value(target.getRow(row), col))) {
                    throw new IllegalArgumentException("Output differs from source at row " + (row + 1));
                }
            }
        }
    }

    private static String value(Row row, int column) {
        Cell cell = row == null ? null : row.getCell(column);
        return cell == null ? "" : cell.toString();
    }

    private static double number(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        return Double.parseDouble(value(row, column));
    }

    private static int update(Sheet sheet, String calcType, List<Passage> passages,
                              Map<String, List<TrainGlobal.Leg>> plan,
                              Map<String, NavigableMap<Double, Tick>> ticks, double interval) {
        Row header = sheet.getRow(0);
        int calc = column(header, "calc type");
        int train = column(header, "train");
        int from = column(header, "from");
        int to = column(header, "to");
        int start = column(header, "start position [m]");
        int end = column(header, "end position [m]");
        int duration = column(header, "achieved end time [s]");
        int consumed = column(header, DIFF_CONSUMED);
        int regenerated = column(header, DIFF_REGENERATED);
        int updated = 0;
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || !calcType.strip().equalsIgnoreCase(value(row, calc).strip())) {
                continue;
            }
            String pattern = new DataFormatter(Locale.ROOT).formatCellValue(row.getCell(train)).strip();
            double startM = number(row, start);
            double endM = number(row, end);
            double durationS = number(row, duration);
            Optional<Passage> match = plan != null
                    ? plannedPassage(plan, ticks, pattern, value(row, from), value(row, to), calcType, interval)
                    : passages.stream()
                    .filter(p -> p.matches(pattern, startM, endM, durationS))
                    .min(Comparator.comparing(Passage::endsBeforeStop)
                            .thenComparingDouble(Passage::startS)
                            .thenComparing(Passage::train));
            if (match.isEmpty()) {
                System.err.printf("No complete match for row %d (%s %s to %s)%n",
                        row.getRowNum() + 1, pattern, value(row, from), value(row, to));
                continue;
            }
            Passage p = match.get();
            row.createCell(consumed).setCellValue(p.consumedJ / J_PER_KWH);
            row.createCell(regenerated).setCellValue(p.regeneratedJ / J_PER_KWH);
            System.out.printf(Locale.ROOT, "Row %d: %s %.3f to %.3f s%n",
                    row.getRowNum() + 1, p.train, p.startS, p.endS);
            if (p.endsBeforeStop) {
                System.out.printf(Locale.ROOT,
                        "  Terminal sample accepted: %.3f s before profile end, %.3f m from endpoint%n",
                        durationS - (p.endS - p.startS), Math.abs(endM - p.endM));
            }
            updated++;
        }
        return updated;
    }

    private static Map<String, NavigableMap<Double, Tick>> readTrainTicks(Path file) throws IOException {
        Map<String, NavigableMap<Double, Tick>> trains = new HashMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            String expected = "time_s,project,scenario,base_hash,object_type,object_id,signal,value,unit,stage,iter,note";
            if (!expected.equals(reader.readLine())) {
                throw new IllegalArgumentException("Unexpected longtable header");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                // The free-text note is the final field and may contain commas.
                String[] fields = line.split(",", 12);
                if (fields.length != 12) {
                    throw new IllegalArgumentException("Invalid longtable row: " + line);
                }
                if (fields[0].isEmpty() || !"TRAIN".equals(fields[4])) continue;
                String signal = fields[6];
                String stage = fields[9];
                boolean selected = switch (signal) {
                    case "p_dev_W", "p_delta_W" -> "RESULT".equals(stage);
                    case "electric_route_id", "electric_route_position_m", "speed_mps" -> "INPUT".equals(stage);
                    default -> false;
                };
                if (!selected) continue;
                double time = finite(fields[0]);
                Tick tick = trains.computeIfAbsent(fields[5], ignored -> new TreeMap<>())
                        .computeIfAbsent(time, ignored -> new Tick());
                tick.set(signal, fields[7]);
            }
        }

        return trains;
    }

    private static Optional<Passage> plannedPassage(
            Map<String, List<TrainGlobal.Leg>> plan, Map<String, NavigableMap<Double, Tick>> ticks,
            String sourceTrain, String from, String to, String calcType, double interval) {
        Passage best = null;
        TrainGlobal.Profile matchedProfile = null;
        for (var entry : plan.entrySet()) {
            for (TrainGlobal.Leg leg : entry.getValue()) {
                TrainGlobal.Profile profile = leg.profile();
                String filename = profile.file().getFileName().toString();
                if (!filename.regionMatches(true, 0, sourceTrain + "_", 0, sourceTrain.length() + 1)
                        || !leg.from().strip().equalsIgnoreCase(from.strip())
                        || !leg.to().strip().equalsIgnoreCase(to.strip())
                        || !matchesSheet(calcType.strip(), profile.sheet())) {
                    continue;
                }
                if (matchedProfile != null && !matchedProfile.equals(profile)) {
                    throw new IllegalArgumentException("Ambiguous profiles for " + sourceTrain + " " + from + " to " + to);
                }
                matchedProfile = profile;
                Passage candidate = integrateLeg(entry.getKey(), leg, ticks.get(entry.getKey()), interval);
                if (candidate != null && (best == null
                        || candidate.startS < best.startS
                        || (candidate.startS == best.startS && candidate.train.compareTo(best.train) < 0))) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean matchesSheet(String calcType, String sheet) {
        return calcType.equalsIgnoreCase(sheet)
                || ("Follow".equalsIgnoreCase(calcType) && "followCalc_".equalsIgnoreCase(sheet));
    }

    private static Passage integrateLeg(String train, TrainGlobal.Leg leg,
                                        NavigableMap<Double, Tick> samples, double interval) {
        if (samples == null || samples.floorKey(leg.startS()) == null
                || samples.lastKey() < leg.endS() - TrainGlobal.ARRIVAL_TIME_TOLERANCE_S) {
            return null;
        }
        Double endSample = samples.ceilingKey(leg.endS());
        double availableEnd = endSample != null ? endSample : samples.lastKey();
        double end = Math.min(leg.endS(), availableEnd);
        if (end <= leg.startS()) return null;

        double previous = samples.floorKey(leg.startS());
        double consumed = 0;
        double regenerated = 0;
        for (var entry : samples.subMap(previous, false, availableEnd, true).entrySet()) {
            double time = entry.getKey();
            Tick tick = entry.getValue();
            if (Math.abs(time - previous - interval) > Math.max(1e-8, interval * 1e-6)
                    || tick.deltaW == null || !leg.route().equalsIgnoreCase(tick.route)) {
                return null;
            }
            double dt = Math.min(time, end) - Math.max(previous, leg.startS());
            consumed += Math.max(tick.deltaW, 0) * dt;
            regenerated += Math.min(tick.deltaW, 0) * dt;
            previous = time;
        }
        return new Passage(train, leg.route(), leg.startS(), end, 0, 0, consumed, regenerated, false);
    }

    private static void collectPassages(String train, NavigableMap<Double, Tick> ticks,
                                        double interval, List<Passage> passages) {
        Map.Entry<Double, Tick> departure = null;
        double previous = Double.NaN;
        boolean moving = false;
        boolean covered = true;
        double consumed = 0;
        double regenerated = 0;
        double lastTime = ticks.lastKey();
        for (var entry : ticks.entrySet()) {
            Tick tick = entry.getValue();
            double time = entry.getKey();
            double dt = time - previous;
            previous = time;
            if (departure != null) {
                covered &= Math.abs(dt - interval) <= Math.max(1e-8, interval * 1e-6)
                        && tick.deltaW != null && tick.speed != null
                        && departure.getValue().route.equalsIgnoreCase(tick.route);
                if (covered) {
                    consumed += Math.max(tick.deltaW, 0) * dt;
                    regenerated += Math.min(tick.deltaW, 0) * dt;
                }
            }
            boolean stopped = tick.speed != null && tick.speed == 0;
            // A run may end between export ticks, before a zero-speed sample is written.
            boolean terminalSample = time == lastTime;
            if ((stopped || terminalSample) && departure != null && covered
                    && (moving || !stopped) && tick.position != null) {
                passages.add(new Passage(train, tick.route, departure.getKey(), time,
                        departure.getValue().position, tick.position, consumed, regenerated, !stopped));
            }
            if (!stopped) {
                moving = true;
                continue;
            }
            departure = tick.route != null && tick.position != null ? entry : null;
            moving = false;
            covered = true;
            consumed = 0;
            regenerated = 0;
        }
    }

    private static double finite(String value) {
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Non-finite longtable value: " + value);
        }
        return number;
    }

    private static void save(Workbook book, Path destination) throws IOException {
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "train-leg-global-", ".xlsx");
        try {
            try (var stream = Files.newOutputStream(temp)) {
                book.write(stream);
            }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private record Passage(String train, String route, double startS, double endS,
                           double startM, double endM, double consumedJ, double regeneratedJ,
                           boolean endsBeforeStop) {
        boolean matches(String pattern, double expectedStartM, double expectedEndM, double durationS) {
            double remainingS = durationS - (endS - startS);
            return (route.equalsIgnoreCase(pattern) || train.equalsIgnoreCase(pattern)
                    || train.regionMatches(true, 0, pattern + "-", 0, pattern.length() + 1))
                    && Math.abs(startM - expectedStartM) <= POSITION_TOLERANCE_M
                    && Math.abs(endM - expectedEndM) <= POSITION_TOLERANCE_M
                    && Math.abs(remainingS) <= DURATION_TOLERANCE_S
                    && (!endsBeforeStop || remainingS >= 0);
        }
    }

    private static final class Tick {
        String route;
        Double position;
        Double speed;
        Double deltaW;

        void set(String signal, String value) {
            switch (signal) {
                case "electric_route_id" -> route = once(route, value);
                case "electric_route_position_m" -> position = once(position, finite(value));
                case "speed_mps" -> speed = once(speed, finite(value));
                case "p_dev_W", "p_delta_W" -> deltaW = once(deltaW, finite(value));
                default -> throw new IllegalArgumentException("Unexpected signal: " + signal);
            }
        }

        private static <T> T once(T previous, T value) {
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate longtable signal at one train timestep");
            }
            return value;
        }
    }
}
