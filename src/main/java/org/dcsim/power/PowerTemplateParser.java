package org.dcsim.power;

import com.typesafe.config.Config;
import org.dcsim.PowerPoint;

import java.io.File;
import java.time.Duration;
import java.util.*;

/**
 * Stöder två sätt att beskriva effektprofiler under dcsim.powerProfiles.templates:
 *
 * 1) Enkel, konstant last (rekommenderad för tester):
 *    templates = [
 *      { id = "T1", constantKW = 1000.0, duration = "00:10:00" }
 *    ]
 *
 *    Skapar två punkter: (t=0, P=+1000 kW) och (t=600, P=+1000 kW).
 *
 * 2) Legacy "folder/legs" (valfritt):
 *    templates = [
 *      {
 *        id = "T1"
 *        folder = "input/loads/T1"
 *        legs = [
 *          { file = "A-B.xlsx" },
 *          { file = "B-C.xlsx" }
 *        ]
 *      }
 *    ]
 *
 *    Kräver Excel-läsare (ExcelProfileReader) – lämna oförändrat om du använder varianten ovan.
 */
public final class PowerTemplateParser {

    private PowerTemplateParser() {}

    public static Map<String, List<PowerPoint>> parse(Config powerProfilesRoot) {
        Map<String, List<PowerPoint>> out = new HashMap<>();

        if (!powerProfilesRoot.hasPath("templates")) return out;

        List<? extends Config> templates = powerProfilesRoot.getConfigList("templates");
        for (Config tpl : templates) {
            String id = tpl.getString("id");

            // (A) Enkel: constantKW + duration
            if (tpl.hasPath("constantKW")) {
                double kw = tpl.getDouble("constantKW");   // + => motoring, − => regen
                double w  = kw * 1000.0;

                double durSec = 0.0;
                if (tpl.hasPath("duration")) {
                    durSec = parseHmsToSeconds(tpl.getString("duration"));
                } else if (tpl.hasPath("seconds")) {
                    durSec = tpl.getDouble("seconds");
                } else {
                    // rimlig default: 10 minuter
                    durSec = 600.0;
                }

                List<PowerPoint> pts = new ArrayList<>(2);
                // PowerPoint(double time, String position, double speed, double power, double voltage, double current)
                pts.add(new PowerPoint(0.0,     "inline", 0.0, w, 0.0, 0.0));
                pts.add(new PowerPoint(durSec,  "inline", 0.0, w, 0.0, 0.0));

                out.put(id, pts);
                continue;
            }

            // (B) Legacy: folder/legs
            if (tpl.hasPath("folder") && tpl.hasPath("legs")) {
                String folder = tpl.getString("folder");
                List<? extends Config> legs = tpl.getConfigList("legs");

                List<PowerPoint> merged = new ArrayList<>();
                for (Config leg : legs) {
                    String fileName = leg.getString("file");
                    // Läs Excel – lämna som hos dig (förutsatt ExcelProfileReader.read(File))
                     List<PowerPoint> points = ExcelProfileReader.read(new File(folder, fileName));
                    // Här gör vi en försiktig fallback om du kör utan Excel:
//                    List<PowerPoint> points = Collections.emptyList();
                    if (points != null && !points.isEmpty()) merged.addAll(points);
                }
                merged.sort(Comparator.comparingDouble(PowerPoint::time));
                out.put(id, merged);
                continue;
            }

            // Okänt format → tom profil (ingen last)
            out.put(id, Collections.emptyList());
        }

        return out;
    }

    private static double parseHmsToSeconds(String hms) {
        // Tillåter "HH:mm:ss" eller "mm:ss" – använder java.time.Duration via "PT…"
        // Ex: "00:10:00" -> 600s
        String[] parts = hms.split(":");
        if (parts.length == 3) {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int s = Integer.parseInt(parts[2]);
            return Duration.ofHours(h).plusMinutes(m).plusSeconds(s).getSeconds();
        } else if (parts.length == 2) {
            int m = Integer.parseInt(parts[0]);
            int s = Integer.parseInt(parts[1]);
            return Duration.ofMinutes(m).plusSeconds(s).getSeconds();
        } else {
            // fallback: tolka som sekunder
            return Double.parseDouble(hms);
        }
    }
}
