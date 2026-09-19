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
        List<Boolean> motoringAndAuxiliariesInSameModel,
        List<Double> auxiliaryPowersW,
        int simulationStartSec,
        int simulationEndSec,
        double exportResolutionS,
        List<RunSource> runSources
) {
    public RunCsvInput(
            List<Path> runExcels,
            List<String> runExcelSheets,
            List<String> trainIds,
            List<String> sectionIds,
            List<String> trackIds,
            List<String> routeIds,
            List<Integer> departureTimes,
            List<Integer> relativeLegDepartureTimes,
            List<Boolean> motoringAndAuxiliariesInSameModel,
            List<Double> auxiliaryPowersW,
            int simulationStartSec,
            int simulationEndSec,
            double exportResolutionS
    ) {
        this(
                runExcels, runExcelSheets, trainIds, sectionIds, trackIds,
                routeIds, departureTimes, relativeLegDepartureTimes,
                motoringAndAuxiliariesInSameModel, auxiliaryPowersW,
                simulationStartSec, simulationEndSec, exportResolutionS,
                excelSources(runExcels, runExcelSheets)
        );
    }

    public RunCsvInput(
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
        this(
                runExcels,
                runExcelSheets,
                trainIds,
                sectionIds,
                trackIds,
                routeIds,
                departureTimes,
                relativeLegDepartureTimes,
                java.util.Collections.nCopies(runExcels.size(), true),
                java.util.Collections.nCopies(runExcels.size(), 0.0),
                simulationStartSec,
                simulationEndSec,
                exportResolutionS,
                excelSources(runExcels, runExcelSheets)
        );
    }

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
                java.util.Collections.nCopies(runExcels.size(), true),
                java.util.Collections.nCopies(runExcels.size(), 0.0),
                simulationStartSec,
                simulationEndSec,
                exportResolutionS,
                excelSources(runExcels, runExcelSheets)
        );
    }

    private static List<RunSource> excelSources(
            List<Path> files,
            List<String> sheets
    ) {
        java.util.ArrayList<RunSource> result = new java.util.ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            result.add(RunSource.excel(files.get(i), sheets.get(i)));
        }
        return java.util.List.copyOf(result);
    }
}
