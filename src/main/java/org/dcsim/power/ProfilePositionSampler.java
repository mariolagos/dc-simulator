/*
package org.dcsim.power;

import org.dcsim.PowerPoint;
import org.dcsim.utils.PositionUtils;

import java.util.List;

*/
/** Linjär interpolering mellan profilpunkter för position. *//*

public final class ProfilePositionSampler {

    private final List<PowerPoint> pts;

    public ProfilePositionSampler(List<PowerPoint> points) {
        this.pts = points;
    }

    */
/** Returnerar tolkad position (lineId + absMeters) vid tiden tSec. *//*

    public PositionAtTime sample(double tSec, int defaultLineId) {
        if (pts == null || pts.isEmpty()) return null;

        // clamp
        if (tSec <= pts.get(0).time()) {
            return parse(pts.get(0).bisPosition(), defaultLineId);
        }
        if (tSec >= pts.get(pts.size()-1).time()) {
            return parse(pts.get(pts.size()-1).bisPosition(), defaultLineId);
        }

        // hitta segment
        int hi = 1;
        while (hi < pts.size() && pts.get(hi).time() < tSec) hi++;
        int lo = hi - 1;

        PositionUtils.Pos pLo = parse(pts.get(lo).bisPosition(), defaultLineId);
        PositionUtils.Pos pHi = parse(pts.get(hi).bisPosition(), defaultLineId);

        double tLo = pts.get(lo).time();
        double tHi = pts.get(hi).time();
        double a = (tSec - tLo) / Math.max(1e-9, (tHi - tLo));

        // Interpolera bara längs samma lineId; annars välj närmast i tid
        if (pLo.lineId != pHi.lineId) {
            return (a < 0.5) ? new PositionAtTime(pLo.lineId, pLo.absMeters)
                    : new PositionAtTime(pHi.lineId, pHi.absMeters);
        }
        double abs = pLo.absMeters + a * (pHi.absMeters - pLo.absMeters);
        return new PositionAtTime(pLo.lineId, abs);
    }

    private static PositionUtils.Pos parse(String s, int defaultLineId) {
        return PositionUtils.parseFlexible(s, defaultLineId);
    }

    public static final class PositionAtTime {
        public final int lineId;
        public final double absMeters;
        public PositionAtTime(int lineId, double absMeters) {
            this.lineId = lineId; this.absMeters = absMeters;
        }
    }
}
*/
