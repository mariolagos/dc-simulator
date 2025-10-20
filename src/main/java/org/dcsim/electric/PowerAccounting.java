package org.dcsim.electric;

import org.dcsim.math.Real;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PowerAccounting {
    private PowerAccounting() {}

    public static GridResult.Totals compute(GridModel model, GridResult res) {
        double pStations = 0.0;
        double pTrains   = 0.0;
        double pLineLoss = 0.0;
        double pBrake    = 0.0;
        double pReqTr    = 0.0;

        for (Object o : model.getDeviceIds()) {
            String devId = java.util.Objects.toString(o, null);
            Device<Real> dev = model.getDevice(devId);
            double p = asDouble(res.getPower(devId));

            if (dev instanceof Substation) {
                pStations += p;
            } else if (dev instanceof Line) {
                pLineLoss += Math.max(0.0, p);
            } else if (dev instanceof TrainLoad) {
                pTrains += p;
            }
        }

        for (String tid : trainIds(model)) {
            pBrake += asDouble(res.getPower(tid + "#brake"));
            pReqTr += asDouble(res.getRequestedPower(tid));
        }

        double mismatch        = pStations - (pTrains + pLineLoss);
        double undersupply     = Math.max(0.0, (pTrains + pLineLoss) - pStations);
        double underreceptivity= Math.max(0.0, pBrake);

        double anchorPsum = 0.0;
        Map<String, List<Real>> updP = model.getUpdatedDevicePowers();
        if (updP != null) {
            for (Map.Entry<String, List<Real>> e : updP.entrySet()) {
                List<Real> ser = e.getValue();
                if (!ser.isEmpty()) {
                    anchorPsum += ser.get(ser.size() - 1).asDouble(); // senaste värdet detta tick
                }
            }
        }
        GridResult.Totals totals = new GridResult.Totals(
                pStations, pTrains, pLineLoss, pBrake,
                pReqTr, mismatch, undersupply, underreceptivity
        );
        totals.pTrains += anchorPsum;
        return totals;
    }

    private static double asDouble(Real r) { return (r == null) ? 0.0 : r.asDouble(); }

    private static List<String> trainIds(GridModel model) {
        List<String> ids = new ArrayList<>();
        for (Object o : model.getDeviceIds()) {
            String devId = java.util.Objects.toString(o, null);
            if (model.getDevice(devId) instanceof TrainLoad) ids.add(devId);
        }
        return ids;
    }
}
