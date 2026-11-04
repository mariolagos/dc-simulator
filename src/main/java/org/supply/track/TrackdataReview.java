package org.supply.track;

import com.typesafe.config.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit approvals, bound to exact interval identity and measured distance. */
public final class TrackdataReview {
    public record Interval(String sheet, String section, String track, int fromM, int toM, double distanceM) { }
    private final Map<Interval,String> accepted;
    private TrackdataReview(Map<Interval,String> accepted) { this.accepted = Map.copyOf(accepted); }
    public static TrackdataReview empty() { return new TrackdataReview(Map.of()); }
    public String reason(Interval interval) { return accepted.get(interval); }
    public Set<Interval> acceptedIntervals() { return accepted.keySet(); }
    public static TrackdataReview read(Path file) {
        Config config = ConfigFactory.parseFile(file.toFile(), ConfigParseOptions.defaults().setAllowMissing(false)).resolve();
        if (!config.root().keySet().equals(Set.of("coordinate_intervals")))
            throw new IllegalArgumentException("Review file requires only coordinate_intervals");
        Set<String> fields = Set.of("sheet", "section", "track", "from", "to", "physical_distance_m", "accepted", "reason");
        Map<Interval,String> accepted = new LinkedHashMap<>(); Set<Interval> seen = new HashSet<>();
        for (Config row : config.getConfigList("coordinate_intervals")) {
            if (!row.root().keySet().equals(fields)) throw new IllegalArgumentException("Invalid review fields; expected " + fields);
            String sheet = row.getString("sheet"), section = row.getString("section"), track = row.getString("track");
            double distance = row.getDouble("physical_distance_m");
            if (sheet.isBlank() || section.isBlank() || !Double.isFinite(distance) || distance <= 0)
                throw new IllegalArgumentException("Invalid sheet, section or physical distance in review file");
            int from = RwyCoordinateParser.parse(section + " " + row.getString("from")).getPositionM();
            int to = RwyCoordinateParser.parse(section + " " + row.getString("to")).getPositionM();
            Interval interval = new Interval(sheet, section, track.isBlank() ? null : track, from, to, distance);
            if (!seen.add(interval)) throw new IllegalArgumentException("Duplicate review interval: " + interval);
            if (row.getBoolean("accepted")) {
                String reason = row.getString("reason").trim();
                if (reason.isEmpty()) throw new IllegalArgumentException("Accepted review interval requires a reason: " + interval);
                accepted.put(interval, reason);
            }
        }
        return new TrackdataReview(accepted);
    }
    /** Creates a review template once. Existing human decisions are never overwritten. */
    public static void createTemplate(Path file, List<Interval> intervals) throws Exception {
        List<Map<String,Object>> rows = new ArrayList<>();
        for (Interval i : new LinkedHashSet<>(intervals)) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("sheet",i.sheet()); row.put("section",i.section()); row.put("track",i.track()==null?"":i.track());
            row.put("from",RwyCoordinate.formatKmShort(i.fromM())); row.put("to",RwyCoordinate.formatKmShort(i.toM()));
            row.put("physical_distance_m",i.distanceM()); row.put("accepted",false); row.put("reason",""); rows.add(row);
        }
        Config config = ConfigFactory.empty().withValue("coordinate_intervals",ConfigValueFactory.fromIterable(rows));
        Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Files.writeString(file,"# Set accepted=true and give a reason only after reviewing the exact interval.\n"
                +config.root().render(ConfigRenderOptions.defaults().setJson(false).setOriginComments(false)),
                StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
