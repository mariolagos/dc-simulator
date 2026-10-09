package org.supply.app;

import org.supply.solver.model.CalculationTrainPosition;
import java.util.List;

/** Concise console output; the check report retains every recorded diagnostic. */
final class InputCheckDiagnostics {
    private static final int CONSOLE_LIMIT = 20;
    private InputCheckDiagnostics() { }

    static String placementFailure(double timeS, CalculationTrainPosition train, Exception failure) {
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return "Train placement: time_s=" + timeS + " train=" + train.trainId()
                + " route=" + train.routeId() + " section=" + train.sectionId()
                + " track=" + train.trackId() + " position_m=" + train.positionM()
                + " route_position_m=" + train.routePositionM() + ": " + reason;
    }

    static String consoleSummary(String report, List<String> errors, List<String> warnings) {
        int end = report.indexOf("\n\n");
        StringBuilder out = new StringBuilder(end < 0 ? report : report.substring(0, end));
        out.append("\n\n");
        append(out, "ERROR", errors);
        append(out, "WARNING", warnings);
        out.append("Full diagnostics and topology details are in the check report.\n");
        return out.toString();
    }

    private static void append(StringBuilder out, String kind, List<String> messages) {
        int shown = Math.min(CONSOLE_LIMIT, messages.size());
        for (int i = 0; i < shown; i++) out.append(kind).append(' ').append(messages.get(i)).append('\n');
        if (shown < messages.size()) out.append(kind).append(": ").append(messages.size() - shown)
                .append(" further messages in the check report.\n");
    }
}
