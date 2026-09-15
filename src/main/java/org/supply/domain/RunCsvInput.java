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
        List<Integer> relativeLegDepartureTimes,
        int simulationStartSec,
        int simulationEndSec,
        double exportResolutionS
) {
    public RunCsvInput(
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
        this(
                runExcels,
                runExcelSheets,
                trainIds,
                sectionIds,
                trackIds,
                routeIds,
                departureTimes,
                java.util.Collections.nCopies(runExcels.size(), null),
                simulationStartSec,
                simulationEndSec,
                exportResolutionS
        );
    }
}
