package org.supply.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Orders directed FROM-TO filenames without reading their contents. */
public final class StationFileChain {
    private StationFileChain() { }

    public static List<Path> order(List<Path> files, String prefix, String suffix) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No leg files match " + prefix + "*" + suffix);
        }
        Map<String, Path> outgoing = new HashMap<>();
        Map<String, String> destinations = new HashMap<>();
        Set<String> incoming = new HashSet<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (!name.startsWith(prefix) || !name.endsWith(suffix)
                    || name.length() <= prefix.length() + suffix.length()) {
                throw new IllegalArgumentException("Invalid leg filename: " + file);
            }
            String pair = name.substring(prefix.length(), name.length() - suffix.length());
            int dash = pair.indexOf('-');
            if (dash <= 0 || dash == pair.length() - 1 || dash != pair.lastIndexOf('-')) {
                throw new IllegalArgumentException("Expected FROM-TO in leg filename: " + file);
            }
            String from = pair.substring(0, dash);
            String to = pair.substring(dash + 1);
            if (!from.equals(from.trim()) || !to.equals(to.trim()) || from.equals(to)) {
                throw new IllegalArgumentException("Invalid stations in leg filename: " + file);
            }
            if (outgoing.putIfAbsent(from, file) != null) {
                throw new IllegalArgumentException("Ambiguous outgoing legs at station " + from);
            }
            if (!incoming.add(to)) {
                throw new IllegalArgumentException("Ambiguous incoming legs at station " + to);
            }
            destinations.put(from, to);
        }
        List<String> starts = outgoing.keySet().stream().filter(s -> !incoming.contains(s)).toList();
        if (starts.size() != 1) {
            throw new IllegalArgumentException("Leg files must form one open station chain; found "
                    + starts.size() + " starting stations");
        }
        List<Path> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String station = starts.get(0);
        while (outgoing.containsKey(station)) {
            if (!visited.add(station)) {
                throw new IllegalArgumentException("Cycle at station " + station);
            }
            ordered.add(outgoing.get(station));
            station = destinations.get(station);
        }
        if (ordered.size() != files.size()) {
            throw new IllegalArgumentException("Disconnected or cyclic leg files outside the station chain");
        }
        return List.copyOf(ordered);
    }
}
