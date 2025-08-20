// org.dcsim.traffic/TrafficLoader.java
package org.dcsim.traffic;

import com.typesafe.config.Config;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Topology;
import org.dcsim.electric.TrainLoad;
import org.dcsim.power.PowerBinding;
import org.dcsim.power.ProfileSampler;
import org.dcsim.profiles.TrainRuntime;
import org.dcsim.runtime.DwellDetector;

import java.time.Instant;
import java.util.*;

public final class TrafficLoader {

    public static final class TrainContext {
        public final String trainId;
        public final TrainLoad device;
        public final TrainRuntime runtime;

        public TrainContext(String trainId, TrainLoad device, TrainRuntime runtime) {
            this.trainId = trainId;
            this.device = device;
            this.runtime = runtime;
        }
    }

    /** Build train runtimes & devices from traffic + powerProfiles. */
    public static List<TrainContext> load(Config rootDcsim,
                                          GridModel model,
                                          Topology topology,
                                          Instant simulationStart,
                                          double auxiliaryPowerKW,
                                          boolean motoringAndAuxTogether) {
        // 1) Parse timetable trains and expand headways
        List<ExpandedTrain> expanded = TimetableExpander.expand(rootDcsim.getConfig("traffic"));

        // 2) Parse templates: route and dwell windows
        Map<String, TrainTemplate> templates = TemplateParser.parse(rootDcsim.getConfig("traffic.templates"));

        // 3) Build samplers from powerProfiles
        PowerBinding binding = PowerBinding.fromConfig(rootDcsim.getConfig("powerProfiles"));

        List<TrainContext> out = new ArrayList<>();
        for (ExpandedTrain t : expanded) {
            TrainTemplate route = templates.get(t.templateId());
            if (route == null) {
                throw new IllegalArgumentException("Missing traffic template: " + t.templateId());
            }

            // Per-train sampler assembled from leg files
            ProfileSampler sampler = binding.buildSamplerForTemplate(route);

            // DwellDetector from arrival/dep times of the route (relative to train departure)
            DwellDetector dwell = DwellDetector.fromTemplate(route, t.departureInstant(simulationStart));

            // Create TrainLoad device (attach to ground-connected segment; nodes may be moved later if you want)
            // For a first pass, attach to a reasonable node (e.g., starting station node or nearest node to start pos).
            int fromNode = model.getGroundNodeId();
            int toNode   = model.getGroundNodeId(); // replace with better mapping if needed
            TrainLoad device = new TrainLoad(t.id(), fromNode, toNode);
            model.addDevice(device);

            // Build TrainRuntime (feeds device per tick)
            TrainRuntime runtime = new TrainRuntime(
                    t.id(),
                    sampler,
                    dwell,
                    topology,
                    auxiliaryPowerKW,
                    motoringAndAuxTogether
            );

            out.add(new TrainContext(t.id(), device, runtime));
        }
        return out;
    }
}
