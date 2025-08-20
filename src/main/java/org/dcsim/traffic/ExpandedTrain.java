// org.dcsim.traffic/ExpandedTrain.java
package org.dcsim.traffic;

import java.time.*;

public record ExpandedTrain(String id, String templateId, Instant departureBase) {
    public Instant departureInstant(Instant simulationStart) {
        // if your times are local/offset strings, compute absolute Instant here relative to simulationStart
        return departureBase;
    }
}
