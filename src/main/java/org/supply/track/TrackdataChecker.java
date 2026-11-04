package org.supply.track;

import org.apache.poi.ss.usermodel.*;
import java.nio.file.*;
import java.util.*;

/** Trackdata-only validation. Does not load an electrical grid or solve a timestep. */
public final class TrackdataChecker {
    private static final List<String> REQUIRED = List.of("position [m]", "trackSection", "bisKm", "bisMeter");
    public enum Level { ERROR, WARNING, INFO }
    public record Diagnostic(Level level, String sheet, int row, String message) { }
    public record Interval(String sheet, String section, String track, int firstRow, int lastRow,
                           String fromRwy, String toRwy, double distanceM) { }
    public record Report(List<Diagnostic> diagnostics, List<Interval> intervals, int sheets, int samples,
                         List<TrackdataReview.Interval> coordinateIntervals) {
        public Report { diagnostics = List.copyOf(diagnostics); intervals = List.copyOf(intervals); coordinateIntervals = List.copyOf(coordinateIntervals); }
        public long errors() { return diagnostics.stream().filter(d -> d.level() == Level.ERROR).count(); }
        public long warnings() { return diagnostics.stream().filter(d -> d.level() == Level.WARNING).count(); }
        public String text() {
            StringBuilder out = new StringBuilder("TRACKDATA CHECK\nStatus: ")
                    .append(errors() == 0 ? "PASS" : "FAIL").append("; errors=").append(errors())
                    .append("; warnings=").append(warnings()).append('\n')
                    .append("Sheets checked: ").append(sheets).append("; samples: ").append(samples).append("\n\n");
            for (Diagnostic d : diagnostics) out.append(d.level()).append(" [").append(d.sheet())
                    .append(d.row() > 0 ? ", row " + d.row() : "").append("] ").append(d.message()).append('\n');
            out.append("\nSECTION / TRACK INTERVALS\n");
            for (Interval i : intervals) out.append(i.sheet()).append(" | section=").append(i.section())
                    .append(" | track=").append(i.track()).append(" | rows=").append(i.firstRow()).append('-').append(i.lastRow())
                    .append(" | ").append(i.fromRwy()).append(" -> ").append(i.toRwy())
                    .append(" | physical distance=").append(i.distanceM()).append(" m\n");
            return out.append("\nOnly explicitly accepted, unchanged coordinate intervals are INFO. Other non-unit intervals remain review warnings.\n")
                    .append("Unspecified tracks are checked within each sheet; cross-sheet physical-track consistency cannot be established.\n")
                    .append("No grid coverage, train placement or timestep was checked.\n").toString();
        }
    }
    private record Group(String section, String track, String scope) { }
    private record Sample(int row, double path, RwyCoordinate coordinate) { }
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final List<Interval> intervals = new ArrayList<>();
    private final Map<Group, Map<String, RouteView>> groups = new LinkedHashMap<>();
    private final List<TrackdataReview.Interval> coordinateIntervals = new ArrayList<>();
    private final TrackdataReview review;
    private int sheets, samples;
    private TrackdataChecker(TrackdataReview review) { this.review = Objects.requireNonNull(review); }

    public static Report check(Path input, List<String> selectedSheets) throws Exception {
        return check(input, selectedSheets, TrackdataReview.empty());
    }
    public static Report check(Path input, List<String> selectedSheets, TrackdataReview review) throws Exception {
        TrackdataChecker checker = new TrackdataChecker(review);
        try (var stream = Files.newInputStream(input); Workbook workbook = WorkbookFactory.create(stream)) {
            if (selectedSheets.isEmpty()) {
                for (Sheet sheet : workbook) checker.sheet(input, sheet, false);
            } else {
                for (String name : new LinkedHashSet<>(selectedSheets)) {
                    Sheet sheet = workbook.getSheet(name);
                    if (sheet == null) checker.add(Level.ERROR, name, 0, "Sheet not found");
                    else checker.sheet(input, sheet, true);
                }
            }
        }
        if (checker.sheets == 0) checker.add(Level.ERROR, "workbook", 0, "No trackdata sheets found");
        checker.checkGroups();
        Set<TrackdataReview.Interval> observed = new HashSet<>(checker.coordinateIntervals);
        for (TrackdataReview.Interval approved : review.acceptedIntervals()) {
            if (!selectedSheets.isEmpty() && !selectedSheets.contains(approved.sheet())) continue;
            if (!observed.contains(approved)) checker.add(Level.WARNING, approved.sheet(), 0,
                    "Accepted interval no longer matches the checked data: section " + approved.section()
                    + " track " + approved.track() + " " + RwyCoordinate.formatKmShort(approved.fromM())
                    + " -> " + RwyCoordinate.formatKmShort(approved.toM()) + "; review the approval");
        }
        return new Report(checker.diagnostics, checker.intervals, checker.sheets, checker.samples, checker.coordinateIntervals);
    }
    private void sheet(Path input, Sheet sheet, boolean explicitlySelected) throws Exception {
        String name = sheet.getSheetName();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> columns = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        if (header != null) for (Cell cell : header) {
            String key = formatter.formatCellValue(cell).trim();
            if (!key.isEmpty() && columns.putIfAbsent(key, cell.getColumnIndex()) != null)
                add(Level.ERROR, name, header.getRowNum() + 1, "Duplicate column: " + key);
        }
        if (!explicitlySelected && REQUIRED.stream().noneMatch(columns::containsKey)) {
            add(Level.INFO, name, 0, "Skipped: no trackdata columns"); return;
        }
        sheets++;
        List<String> missing = REQUIRED.stream().filter(key -> !columns.containsKey(key)).toList();
        if (!missing.isEmpty()) { add(Level.ERROR, name, 0, "Missing columns: " + missing); return; }
        List<Sample> valid = new ArrayList<>();
        Set<String> routeIds = new LinkedHashSet<>();
        long errorsBefore = diagnostics.stream().filter(d -> d.level() == Level.ERROR).count();
        for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            boolean empty = REQUIRED.stream().allMatch(key -> text(row, columns, key, formatter).isBlank());
            if (empty) continue;
            try {
                double path = number(row, columns, "position [m]", formatter);
                if (path < 0) throw new IllegalArgumentException("position [m] must be non-negative");
                String section = text(row, columns, "trackSection", formatter);
                if (section.isBlank()) throw new IllegalArgumentException("trackSection is required");
                if (section.endsWith(".0")) section = section.substring(0, section.length() - 2);
                int km = integer(row, columns, "bisKm", formatter), metre = integer(row, columns, "bisMeter", formatter);
                if (metre < 0 || metre > 999) throw new IllegalArgumentException("bisMeter must be 0..999");
                int position = Math.addExact(Math.multiplyExact(km, 1000), metre);
                String track = text(row, columns, "trackNumber", formatter);
                valid.add(new Sample(index + 1, path, new RwyCoordinate(section,
                        RwyCoordinate.formatKmShort(position), position, track.isBlank() ? null : track)));
                String routeId = text(row, columns, "trackInformation", formatter);
                if (!routeId.isBlank()) routeIds.add(routeId);
            } catch (RuntimeException ex) { add(Level.ERROR, name, index + 1, ex.getMessage()); }
        }
        samples += valid.size();
        if (valid.size() < 2) { add(Level.ERROR, name, 0, "At least two valid trackdata rows are required"); return; }
        if (routeIds.size() > 1) add(Level.ERROR, name, 0, "trackInformation contains multiple route IDs");
        // The existing reader owns column, kilometre-board and RouteView validation.
        long errorsNow = diagnostics.stream().filter(d -> d.level() == Level.ERROR).count();
        if (errorsNow == errorsBefore) try {
            new RouteViewExcelReader().read(input, name, routeIds.isEmpty() ? name : routeIds.iterator().next());
        } catch (IllegalArgumentException ex) { add(Level.ERROR, name, 0, ex.getMessage()); }
        if (valid.stream().anyMatch(s -> s.coordinate().getTrackId() == null))
            add(Level.WARNING, name, 0, "Blank trackNumber: cross-sheet same-track checks are unavailable for these rows");
        for (int i = 1; i < valid.size(); i++) {
            Sample a = valid.get(i - 1), b = valid.get(i);
            double distance = b.path() - a.path();
            boolean same = sameTrack(a.coordinate(), b.coordinate());
            if (distance < 0 || (distance == 0 && !a.coordinate().equals(b.coordinate())))
                add(Level.ERROR, name, b.row(), "Physical positions must increase; equal positions require identical coordinates");
            if (distance == 0 && a.coordinate().equals(b.coordinate()))
                add(Level.INFO, name, b.row(), "Identical duplicate of row " + a.row());
            if (!same && distance > 1.0 + 1e-9)
                add(Level.ERROR, name, b.row(), "Section or track transition exceeds 1 m: " + distance + " m");
            if (same && distance > 0) {
                long railwayDistance = Math.abs((long)b.coordinate().getPositionM() - a.coordinate().getPositionM());
                if (railwayDistance == 0) add(Level.ERROR, name, b.row(), "Identical railway coordinate has non-zero physical distance");
                else if (Math.abs(distance - railwayDistance) > 1.0 + 1e-9) {
                    TrackdataReview.Interval interval = new TrackdataReview.Interval(name,
                            a.coordinate().getSectionId(), a.coordinate().getTrackId(),
                            a.coordinate().getPositionM(), b.coordinate().getPositionM(), distance);
                    coordinateIntervals.add(interval);
                    String reason = review.reason(interval);
                    add(reason == null ? Level.WARNING : Level.INFO, name, b.row(),
                            (reason == null ? "Review interval" : "Accepted coordinate interval")
                            + " from row " + a.row() + ": " + distance + " physical m versus "
                            + railwayDistance + " coordinate m" + (reason == null ? "" : "; " + reason));
                }
            }
        }
        int start = 0;
        while (start < valid.size()) {
            int end = start + 1;
            while (end < valid.size() && sameTrack(valid.get(start).coordinate(), valid.get(end).coordinate())) end++;
            interval(name, valid.subList(start, end)); start = end;
        }
    }
    private void interval(String sheet, List<Sample> run) {
        Sample first = run.get(0), last = run.get(run.size() - 1);
        RwyCoordinate c = first.coordinate(); String track = c.getTrackId();
        intervals.add(new Interval(sheet, c.getSectionId(), track == null ? "<unspecified>" : track,
                first.row(), last.row(), c.getPositionText(), last.coordinate().getPositionText(), last.path() - first.path()));
        if (run.size() < 2) { add(Level.WARNING, sheet, first.row(), "Single-sample section/track interval: no distance scale can be checked"); return; }
        try {
            List<PathSample> points = run.stream().map(s -> new PathSample(s.path(), s.coordinate())).toList();
            RouteView view = new RouteView(sheet, sheet, points);
            Group key = new Group(c.getSectionId(), track, track == null ? sheet : "");
            groups.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(sheet + ":" + first.row(), view);
        } catch (IllegalArgumentException ex) { add(Level.ERROR, sheet, first.row(), ex.getMessage()); }
    }
    private void checkGroups() {
        for (var group : groups.entrySet()) {
            // Disjoint observations do not establish a contradiction. Compare
            // only overlapping components, without inventing missing coverage.
            List<Map.Entry<String,RouteView>> views = new ArrayList<>(group.getValue().entrySet());
            views.sort(Comparator.comparingInt(e -> low(e.getValue())));
            Map<String,RouteView> component = new LinkedHashMap<>();
            int high = Integer.MIN_VALUE;
            for (var view : views) {
                if (!component.isEmpty() && low(view.getValue()) > high) {
                    checkComponent(group.getKey(), component); component = new LinkedHashMap<>();
                    high = Integer.MIN_VALUE;
                }
                component.put(view.getKey(), view.getValue());
                high = Math.max(high, high(view.getValue()));
            }
            checkComponent(group.getKey(), component);
        }
    }
    private static int low(RouteView view) {
        return view.getSamples().stream().mapToInt(s -> s.getRailwayCoordinate().getPositionM()).min().orElseThrow();
    }
    private static int high(RouteView view) {
        return view.getSamples().stream().mapToInt(s -> s.getRailwayCoordinate().getPositionM()).max().orElseThrow();
    }
    private void checkComponent(Group key, Map<String,RouteView> views) {
        if (views.isEmpty()) return;
        try { TrackSectionsFromRouteViews.build(views); }
        catch (IllegalArgumentException ex) {
            add(Level.ERROR, String.join(", ", views.keySet()), 0,
                    "Section " + key.section() + " track " + (key.track() == null ? "<unspecified>" : key.track()) + ": " + ex.getMessage());
        }
    }
    private static boolean sameTrack(RwyCoordinate a, RwyCoordinate b) {
        return a.getSectionId().equals(b.getSectionId()) && Objects.equals(a.getTrackId(), b.getTrackId());
    }
    private static String text(Row row, Map<String,Integer> columns, String key, DataFormatter formatter) {
        Integer column = columns.get(key);
        return column == null ? "" : formatter.formatCellValue(row.getCell(column)).trim();
    }
    private static double number(Row row, Map<String,Integer> columns, String key, DataFormatter formatter) {
        Cell cell = row.getCell(columns.get(key));
        double value;
        try { value = cell != null && cell.getCellType() == CellType.NUMERIC ? cell.getNumericCellValue()
                : Double.parseDouble(text(row, columns, key, formatter).replace(',', '.')); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid numeric value in " + key); }
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite value in " + key);
        return value;
    }
    private static int integer(Row row, Map<String,Integer> columns, String key, DataFormatter formatter) {
        double value = number(row, columns, key, formatter);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException(key + " must be an integer within supported range");
        return (int)value;
    }
    private void add(Level level, String sheet, int row, String message) { diagnostics.add(new Diagnostic(level, sheet, row, message)); }
}
