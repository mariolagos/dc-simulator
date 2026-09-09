package org.supply.track;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrackConfigLoader {

    public LoadedTrackModel load(Config dcsim)
            throws Exception {
        return load(dcsim, null);
    }

    public LoadedTrackModel load(
            Config dcsim,
            Path confFile
    ) throws Exception {

        if (!dcsim.hasPath("track")) {
            return new TrackLoader().load(
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        Config track = dcsim.getConfig("track");

        List<KilometerBoard> boards =
                loadKilometerBoards(track);
        List<TrackJunction> junctions =
                loadJunctions(track);
        List<Station> stations =
                loadStations(track);

        LoadedTrackModel sectionModel =
                new TrackLoader().load(
                        boards,
                        junctions,
                        stations
                );

        Map<String, RouteView> routeViews =
                loadRouteViews(
                        track,
                        confFile
                );

        return new LoadedTrackModel(
                sectionModel.getSectionsById(),
                sectionModel.getJunctions(),
                sectionModel.getStations(),
                routeViews
        );    }


    private Map<String, RouteView> loadRouteViews(
            Config track,
            Path confFile
    ) throws Exception {
        boolean hasWorkbook =
                track.hasPath("route_data_excel");
        boolean hasViews =
                track.hasPath("route_views");

        if (!hasWorkbook && !hasViews) {
            return Map.of();
        }

        if (!hasWorkbook || !hasViews) {
            throw new IllegalArgumentException(
                    "track.route_data_excel and "
                            + "track.route_views must be configured together"
            );
        }

        Path workbookPath =
                resolveRelativeToConfig(
                        confFile,
                        track.getString("route_data_excel")
                );

        Map<String, RouteView> result =
                new LinkedHashMap<>();

        RouteViewExcelReader reader =
                new RouteViewExcelReader();

        for (Config routeConfig :
                track.getConfigList("route_views")) {

            String routeId =
                    routeConfig.getString("route_id");

            String sheetName =
                    routeConfig.getString("sheet");

            RouteView previous =
                    result.put(
                            routeId,
                            reader.read(
                                    workbookPath,
                                    sheetName,
                                    routeId
                            )
                    );

            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate track route_id: "
                                + routeId
                );
            }
        }

        return Map.copyOf(result);
    }

    private Path resolveRelativeToConfig(
            Path confFile,
            String pathText
    ) {
        Path path = Path.of(pathText);

        if (path.isAbsolute()) {
            return path.normalize();
        }

        if (confFile == null
                || confFile.toAbsolutePath().getParent() == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve relative track route workbook: "
                            + pathText
            );
        }

        Path resolved =
                confFile.toAbsolutePath()
                        .getParent()
                        .resolve(path)
                        .normalize();

        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(
                    "Track route workbook not found: "
                            + resolved
            );
        }

        return resolved;
    }
    private List<KilometerBoard> loadKilometerBoards(Config track) {
        List<KilometerBoard> result = new ArrayList<>();
        if (!track.hasPath("kilometer_boards")) {
            return result;
        }

        for (Config c : track.getConfigList("kilometer_boards")) {
            String section = c.getString("section");
            String kmText = c.getString("km");
            int kmPositionM = parseKmTextToMeters(kmText);
            int length = c.getInt("length");

            result.add(new KilometerBoard(section, kmText, kmPositionM, length));
        }
        return result;
    }

    private List<TrackJunction> loadJunctions(Config track) {
        List<TrackJunction> result = new ArrayList<>();
        if (!track.hasPath("junctions")) {
            return result;
        }

        for (Config c : track.getConfigList("junctions")) {
            RwyCoordinate from = parseRwyCoordinate(c.getString("from_position_rwy"));
            RwyCoordinate to = parseRwyCoordinate(c.getString("to_position_rwy"));

            result.add(new TrackJunction(from, to));
        }
        return result;
    }

    private List<Station> loadStations(Config track) {
        List<Station> result = new ArrayList<>();
        if (!track.hasPath("stations")) {
            return result;
        }

        for (Config c : track.getConfigList("stations")) {
            String name = c.getString("name");
            String positionRwy = c.getString("position_rwy");
            String trackId = c.hasPath("track_id") ? c.getString("track_id") : null;

            RwyCoordinate coordinate = parseRwyCoordinate(positionRwy, trackId);
            result.add(new Station(name, coordinate));
        }
        return result;
    }

    private RwyCoordinate parseRwyCoordinate(String raw) {
        return parseRwyCoordinate(raw, null);
    }

    private RwyCoordinate parseRwyCoordinate(String raw, String overrideTrackId) {
        // Minimal parser assumption:
        // "<section> <km+m>" or "<section> <km+m> <track>"
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid railway coordinate: " + raw);
        }

        String sectionId = parts[0];
        String kmText = parts[1];
        String trackId = overrideTrackId != null ? overrideTrackId : (parts.length >= 3 ? parts[2] : null);

        return new RwyCoordinate(sectionId, kmText, parseKmTextToMeters(kmText), trackId);
    }

    private int parseKmTextToMeters(String kmText) {
        // Supports formats like:
        // 0+000
        // 12+345
        String[] parts = kmText.split("\\+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid km format: " + kmText);
        }

        int km = Integer.parseInt(parts[0]);
        int meters = Integer.parseInt(parts[1]);

        return km * 1000 + meters;
    }
}