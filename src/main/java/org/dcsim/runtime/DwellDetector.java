// org.dcsim.runtime/DwellDetector.java
package org.dcsim.runtime;

import org.dcsim.traffic.TrainTemplate;

import java.time.*;
import java.util.*;

public final class DwellDetector {
    private final List<Window> windows;

    private DwellDetector(List<Window> windows) { this.windows = windows; }

    public boolean isDwellingAt(Instant absTime) {
        for (var w : windows) {
            if (!absTime.isBefore(w.start) && !absTime.isAfter(w.end)) return true;
        }
        return false;
    }

    public boolean isDwellingAt(double secondsSinceStart, Instant start) {
        Instant t = start.plusSeconds((long) secondsSinceStart);
        return isDwellingAt(t);
    }

    public static DwellDetector fromTemplate(TrainTemplate tpl, Instant firstDeparture) {
        // Build absolute arrival/departure instants from template times
        // (You can keep it simple: only use departure windows for now.)
        return new DwellDetector(List.of());
    }

    private static final class Window {
        final Instant start, end;
        Window(Instant s, Instant e) { this.start = s; this.end = e; }
    }
}
