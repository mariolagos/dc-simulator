package org.supply.domain;

import java.nio.file.Path;
import java.util.List;

public record RunCsvInput(
        List<Path> runExcels,
        List<String> trainIds,
        List<String> sectionIds,
        List<String> trackIds,
        List<String> routeIds,
        List<Integer> departureTimes,
        double exportResolutionS
) {
}