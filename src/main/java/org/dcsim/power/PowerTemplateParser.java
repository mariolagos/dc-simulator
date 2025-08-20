package org.dcsim.power;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import org.dcsim.PowerPoint;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses dcsim.powerProfiles.templates:
 *
 * powerProfiles {
 *   motoringAndAuxiliariesInSameModel = false  // ignored here
 *   auxiliaryPower = 5.0                       // ignored here
 *
 *   templates = [
 *     {
 *       id = "T1"
 *       folder = "input/loads/T1"
 *       legs = [
 *         { fromStation = "A", toStation = "B", file = "A-B.xlsx" }
 *       ]
 *     }
 *   ]
 * }
 *
 * Returns: Map<templateId, List<PowerPoint>>
 */
public final class PowerTemplateParser {

    private PowerTemplateParser() {}

    public static Map<String, List<PowerPoint>> parse(Config powerProfilesRoot) {
        Map<String, List<PowerPoint>> out = new HashMap<>();

        if (!powerProfilesRoot.hasPath("templates")) {
            // Nothing to parse; return empty map
            return out;
        }

        List<? extends Config> templates = powerProfilesRoot.getConfigList("templates");
        for (Config tpl : templates) {
            String id     = tpl.getString("id");
            String folder = tpl.getString("folder");

            List<? extends Config> legs = tpl.getConfigList("legs");
            List<PowerPoint> merged = new ArrayList<>();

            for (Config leg : legs) {
                String fileName = leg.getString("file");   // e.g. "A-B.xlsx"
                File f = new File(folder, fileName);

                // Load one Excel leg → points
//                List<PowerPoint> points = ExcelProfileLoader.loadXlsx(f);
                List<PowerPoint> points = ExcelProfileReader.read(f);
                if (points != null && !points.isEmpty()) {
                    merged.addAll(points);
                }
            }

            // Ensure monotonically nondecreasing time
            merged.sort(Comparator.comparingDouble(PowerPoint::time));

            out.put(id, merged);
        }

        return out;
    }
}
