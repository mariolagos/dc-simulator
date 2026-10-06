package org.supply.app;

import com.typesafe.config.Config;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supply.domain.RunSample;
import org.supply.solver.build.TrainPositionFactory;
import org.supply.solver.io.ResultMetadata;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.track.TrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.PathSample;
import org.supply.track.RouteView;
import org.supply.track.RwyCoordinate;
import org.supply.track.TrackConfigLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.supply.app.DcReporter.*;

final class BaseRoutePositionMapper {
    static BaseRoutePositionMapper loadBaseRoutePositionMapper(
            DcStudyContext context
    ) throws IOException {
        return loadBaseRoutePositionMapper(
                context, context.exportDirectory()
        );
    }

    static BaseRoutePositionMapper loadBaseRoutePositionMapper(
            DcStudyContext context,
            Path exportDirectory
    ) throws IOException {
        String configPath = "report.graphical-timetable";
        Config scenario = context.scenario();

        if (!scenario.hasPath(configPath)) {
            return BaseRoutePositionMapper.disabled();
        }

        Config graphicalTimetable = scenario.getConfig(configPath);
        if (graphicalTimetable.hasPath("enabled")
                && !graphicalTimetable.getBoolean("enabled")) {
            return BaseRoutePositionMapper.disabled();
        }

        // The train workbook retains the first base route as its position axis.
        if (graphicalTimetable.hasPath("base-routes")) {
            List<? extends Config> routes = graphicalTimetable.getConfigList("base-routes");
            if (routes.isEmpty()) throw new IllegalArgumentException("Timetable base-routes must not be empty");
            graphicalTimetable = graphicalTimetable.withValue("base-route", routes.get(0).root());
        }

        if (!graphicalTimetable.hasPath("base-route.sections")) {
            throw new IllegalArgumentException(
                    "Missing configuration: " + configPath
                            + ".base-route.sections"
            );
        }

        List<String> sections =
                graphicalTimetable.getStringList("base-route.sections");

        Path trackSegments = exportDirectory.resolve("track_segments.csv");
        Map<String, Boolean> reversedSections =
                loadReversedBaseRouteSections(
                        context, graphicalTimetable, trackSegments
                );

        return BaseRoutePositionMapper.load(
                trackSegments, sections, reversedSections
        );
    }

    static Map<String, Boolean> loadReversedBaseRouteSections(
            DcStudyContext context,
            Config graphicalTimetable,
            Path trackSegments
    ) throws IOException {
        if (!graphicalTimetable.hasPath("base-route.id")) {
            return Map.of();
        }

        String routeId = graphicalTimetable.getString("base-route.id");
        RouteView route;
        try {
            LoadedTrackModel track = new TrackConfigLoader().load(
                    context.dcsim(), context.confFile()
            );
            route = track.getRouteViewsById().get(routeId);
        } catch (Exception exception) {
            throw new IOException(
                    "Could not load base-route trackdata for " + routeId,
                    exception
            );
        }

        if (route == null) {
            throw new IllegalArgumentException(
                    "Base-route id is missing from track.route_views: "
                            + routeId
            );
        }

        Map<String, SectionDirection> routeDirections =
                routeDirections(route);
        Map<String, List<RailwaySegment>> modelSegments =
                BaseRoutePositionMapper.readRailwaySegments(trackSegments);
        Map<String, Boolean> result = new LinkedHashMap<>();

        for (Map.Entry<String, SectionDirection> entry
                : routeDirections.entrySet()) {
            List<RailwaySegment> segments =
                    modelSegments.get(entry.getKey());
            if (segments == null || segments.isEmpty()) {
                continue;
            }
            double modelStart = segments.get(0).fromRailwayM;
            double modelEnd = segments.get(segments.size() - 1).toRailwayM;
            double routeStart = entry.getValue().firstRailwayM;
            double routeEnd = entry.getValue().lastRailwayM;
            if (modelStart != modelEnd && routeStart != routeEnd) {
                result.put(
                        entry.getKey(),
                        Math.signum(modelEnd - modelStart)
                                != Math.signum(routeEnd - routeStart)
                );
            }
        }
        return Map.copyOf(result);
    }

    static Map<String, SectionDirection> routeDirections(
            RouteView route
    ) {
        Map<String, SectionDirection> result = new LinkedHashMap<>();
        for (PathSample sample : route.getSamples()) {
            RwyCoordinate coordinate = sample.getRailwayCoordinate();
            result.compute(
                    coordinate.getSectionId(),
                    (ignored, direction) -> direction == null
                            ? new SectionDirection(
                            coordinate.getPositionM(),
                            coordinate.getPositionM()
                    )
                            : new SectionDirection(
                            direction.firstRailwayM,
                            coordinate.getPositionM()
                    )
            );
        }
        return result;
    }

    record SectionDirection(
            double firstRailwayM,
            double lastRailwayM
    ) { }

    final Map<String, Double> sectionOffsetsM;
    final Map<String, Double> sectionLengthsM;
    final Map<String, List<RailwaySegment>> segmentsBySection;
    final Map<String, Boolean> reversedSections;

    BaseRoutePositionMapper(
            Map<String, Double> sectionOffsetsM,
            Map<String, Double> sectionLengthsM,
            Map<String, List<RailwaySegment>> segmentsBySection,
            Map<String, Boolean> reversedSections
    ) {
        this.sectionOffsetsM = sectionOffsetsM;
        this.sectionLengthsM = sectionLengthsM;
        this.segmentsBySection = segmentsBySection;
        this.reversedSections = reversedSections;
    }

    static BaseRoutePositionMapper disabled() {
        return new BaseRoutePositionMapper(
                Map.of(), Map.of(), Map.of(), Map.of()
        );
    }

    boolean isEnabled() {
        return !sectionOffsetsM.isEmpty();
    }

    static BaseRoutePositionMapper load(
            Path trackSegmentsPath,
            List<String> baseRouteSections,
            Map<String, Boolean> reversedSections
    ) throws IOException {
        if (baseRouteSections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Base route must contain at least one section"
            );
        }

        Map<String, Double> sectionLengthsM =
                readSectionLengths(trackSegmentsPath);
        Map<String, List<RailwaySegment>> segmentsBySection =
                readRailwaySegments(trackSegmentsPath);
        Map<String, Double> offsetsM = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        double offsetM = 0.0;

        for (String section : baseRouteSections) {
            if (!seen.add(section)) {
                throw new IllegalArgumentException(
                        "Duplicate section in base route: " + section
                );
            }

            Double lengthM = sectionLengthsM.get(section);
            if (lengthM == null) {
                throw new IllegalArgumentException(
                        "Base-route section " + section
                                + " is missing from "
                                + trackSegmentsPath
                );
            }

            offsetsM.put(section, offsetM);
            offsetM += lengthM;
        }

        return new BaseRoutePositionMapper(
                offsetsM,
                sectionLengthsM,
                segmentsBySection,
                reversedSections
        );
    }

    Double map(String sectionId, Double positionM) {
        if (sectionId == null || positionM == null) {
            return null;
        }

        Double offsetM = sectionOffsetsM.get(sectionId);
        if (offsetM == null) {
            return null;
        }
        return offsetM + orientedPosition(sectionId, positionM);
    }

    double orientedPosition(
            String sectionId, double positionM
    ) {
        if (!reversedSections.getOrDefault(sectionId, false)) {
            return positionM;
        }
        Double lengthM = sectionLengthsM.get(sectionId);
        if (lengthM == null) {
            throw new IllegalArgumentException(
                    "Missing base-route section length: " + sectionId
            );
        }
        return lengthM - positionM;
    }

    Double mapRailwayPosition(String railwayPosition) {
        if (railwayPosition == null) {
            return null;
        }
        String[] parts = railwayPosition.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid railway position: " + railwayPosition
            );
        }
        String sectionId = parts[0];
        Double sectionOffsetM = sectionOffsetsM.get(sectionId);
        if (sectionOffsetM == null) {
            return null;
        }
        double railwayPositionM = railwayMetres(parts[1]);
        List<RailwaySegment> segments =
                segmentsBySection.get(sectionId);
        if (segments == null) {
            return null;
        }
        for (RailwaySegment segment : segments) {
            if (segment.contains(railwayPositionM)) {
                return sectionOffsetM
                        + orientedPosition(
                        sectionId,
                        segment.modelPositionM(railwayPositionM)
                );
            }
        }
        throw new IllegalArgumentException(
                "Railway position is outside section " + sectionId
                        + ": " + railwayPosition
        );
    }

    Double mapRailwayCoordinate(String section, double railwayM) {
        for (RailwaySegment segment : segmentsBySection.getOrDefault(section, List.of())) {
            if (segment.contains(railwayM)) return map(section, segment.modelPositionM(railwayM));
        }
        return null;
    }

    static double railwayMetres(String value) {
        int plus = value.indexOf('+');
        if (plus <= 0 || plus == value.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid railway coordinate: " + value
            );
        }
        return Double.parseDouble(value.substring(0, plus)) * 1000.0
                + Double.parseDouble(value.substring(plus + 1));
    }

    static Map<String, List<RailwaySegment>>
    readRailwaySegments(Path trackSegmentsPath) throws IOException {
        Map<String, List<RailwaySegment>> result =
                new LinkedHashMap<>();
        try (BufferedReader reader =
                     Files.newBufferedReader(trackSegmentsPath)) {
            String header = reader.readLine();
            if (!"section,from_rwy,to_rwy,start_model_m,length_m"
                    .equals(header)) {
                throw new IllegalArgumentException(
                        "Unexpected track_segments.csv header in "
                                + trackSegmentsPath + ": " + header
                );
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(",", -1);
                if (fields.length != 5) {
                    throw new IllegalArgumentException(
                            "Invalid track segment row: " + line
                    );
                }
                result.computeIfAbsent(
                        fields[0], ignored -> new ArrayList<>()
                ).add(new RailwaySegment(
                        railwayCoordinateMetres(fields[1]),
                        railwayCoordinateMetres(fields[2]),
                        Double.parseDouble(fields[3]),
                        Double.parseDouble(fields[4])
                ));
            }
        }
        return result;
    }

    static double railwayCoordinateMetres(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid railway position: " + value
            );
        }
        return railwayMetres(parts[1]);
    }

    static Map<String, Double> readSectionLengths(
            Path trackSegmentsPath
    ) throws IOException {
        if (!Files.exists(trackSegmentsPath)) {
            throw new IllegalArgumentException(
                    "Track-segment export not found: "
                            + trackSegmentsPath
            );
        }

        Map<String, Double> lengthsM = new LinkedHashMap<>();

        try (BufferedReader reader =
                     Files.newBufferedReader(trackSegmentsPath)) {
            String header = reader.readLine();
            if (!"section,from_rwy,to_rwy,start_model_m,length_m"
                    .equals(header)) {
                throw new IllegalArgumentException(
                        "Unexpected track_segments.csv header in "
                                + trackSegmentsPath + ": " + header
                );
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] fields = line.split(",", -1);
                if (fields.length != 5) {
                    throw new IllegalArgumentException(
                            "Invalid track-segment row: " + line
                    );
                }

                String section = fields[0];
                double startModelM;
                double lengthM;
                try {
                    startModelM = Double.parseDouble(fields[3]);
                    lengthM = Double.parseDouble(fields[4]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid track-segment coordinate in row: "
                                    + line,
                            e
                    );
                }

                if (startModelM < 0.0 || !(lengthM > 0.0)) {
                    throw new IllegalArgumentException(
                            "Track-segment start must be non-negative "
                                    + "and length must be positive: "
                                    + line
                    );
                }

                double sectionEndM = startModelM + lengthM;
                lengthsM.merge(section, sectionEndM, Math::max);
            }
        }

        return lengthsM;
    }

    static final class RailwaySegment {
        final double fromRailwayM;
        final double toRailwayM;
        final double startModelM;
        final double lengthM;

        RailwaySegment(
                double fromRailwayM,
                double toRailwayM,
                double startModelM,
                double lengthM
        ) {
            this.fromRailwayM = fromRailwayM;
            this.toRailwayM = toRailwayM;
            this.startModelM = startModelM;
            this.lengthM = lengthM;
        }

        boolean contains(double positionM) {
            return positionM >= Math.min(fromRailwayM, toRailwayM)
                    && positionM <= Math.max(fromRailwayM, toRailwayM);
        }

        double modelPositionM(double railwayPositionM) {
            double railwayLengthM = toRailwayM - fromRailwayM;
            if (railwayLengthM == 0.0) {
                return startModelM;
            }
            double fraction =
                    (railwayPositionM - fromRailwayM) / railwayLengthM;
            return startModelM + fraction * lengthM;
        }
    }

}
