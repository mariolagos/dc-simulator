package org.dcsim.testing;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Substation;
import org.dcsim.electric.Line;
import org.dcsim.math.Real;

// lägg till import org.dcsim.electric.DiodeSubstation; om ni har en sådan klass

public final class Devices {
    private Devices() {
    }

    public static void addSubstation(GridModel<?> gm,
                                     String id,
                                     int anchorNode,
                                     double emfV,
                                     double rintOhm,
                                     boolean allowBackFeed,
                                     String description) {
        int g = gm.getGroundNodeId();
        if (allowBackFeed) {
            gm.addDevice(new Substation(id, anchorNode, g, g,
                    Real.fromDouble(emfV), Real.fromDouble(rintOhm), description));
        } else {
            // Om ni har en "diode"/backfeed-blockerande variant – använd den:
            // gm.addDevice(new DiodeSubstation(id, anchorNode, g, g, Real.of(emfV), Real.of(rintOhm), description));
            // Om inte: använd Substation + flagga/behavior om API:t erbjuder det.
            gm.addDevice(new Substation(id, anchorNode, g, g,
                    Real.fromDouble(emfV), Real.fromDouble(rintOhm), description));
            // TODO (post-release): koppla behavior/setAllowBackfeed(false) om stöd finns
        }
    }

    public static void addLine(GridModel<?> gm, String id, int a, int b, double rOhm, double lengthM) {
        String description = a + "-" + b;
        gm.addDevice(new Line(a, b, Real.fromDouble(rOhm), id, "n", lengthM));
    }
}
