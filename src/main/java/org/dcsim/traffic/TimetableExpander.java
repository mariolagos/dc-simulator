// org.dcsim.traffic/TimetableExpander.java
package org.dcsim.traffic;

import com.typesafe.config.Config;

import java.time.*;
import java.util.*;

public final class TimetableExpander {
    private TimetableExpander() {}

    public static List<ExpandedTrain> expand(Config trafficConf) {
        // traffic.timetable.trains = [ { id, templateId, departure, headway, count }, ... ]
        // Expand headway → N trains with id suffixes if needed (e.g., "1023-01", "1023-02", ...)
        // Parse departure as offset/time; convert to Instant later in TrafficLoader.
        return List.of(); // TODO: implement
    }
}
