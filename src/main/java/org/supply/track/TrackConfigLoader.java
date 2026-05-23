package org.supply.track;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import java.util.ArrayList;
import java.util.List;

public final class TrackConfigLoader {

    public LoadedTrackModel load(Config dcsim) {
        if (!dcsim.hasPath("track")) {
            return new TrackLoader().load(List.of(), List.of(), List.of());
        }

        Config track = dcsim.getConfig("track");

        List<KilometerBoard> boards = loadKilometerBoards(track);
        List<TrackJunction> junctions = loadJunctions(track);
        List<Station> stations = loadStations(track);

        return new TrackLoader().load(boards, junctions, stations);
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