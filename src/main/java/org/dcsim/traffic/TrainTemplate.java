// org.dcsim.traffic/TrainTemplate.java
package org.dcsim.traffic;

import java.util.List;

public record TrainTemplate(String id, List<Stop> stops) {
    public static record Stop(String signature, String arrival, String departure) {}
}
