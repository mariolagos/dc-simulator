package org.supply.domain;

import java.nio.file.Path;
import java.util.List;

public record RunCsvInput(
        List<Path> runExcels,
        List<String> runExcelSheets,
        List<String> trainIds,
        List<String> sectionIds,
        List<String> trackIds,
        List<String> routeIds,
        List<Integer> departureTimes,
        int simulationStartSec,
        int simulationEndSec,
        double exportResolutionS
) {
}