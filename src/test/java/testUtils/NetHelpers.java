package testUtils;

import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;

import java.io.File;

public class NetHelpers {

    static double substationPower(SubstationData ss, RealVector V, double eps) {
        double Va = V.getEntry(ss.a()), Vb = V.getEntry(ss.b());
        double dV = Va - Vb;
        double g = 1.0 / ss.rint_ohm();
        boolean active = ss.allowBackfeed() || (dV <= ss.emf_V() + eps);
        double i = active ? g * (ss.emf_V() - dV) : 0.0;
        return i * dV;
    }

    static double sumSubstationPower(DcNet net, RealVector V, double eps) {
        double p = 0.0;
        for (SubstationData ss : net.substations()) p += substationPower(ss, V, eps);
        return p;
    }

    static double trainNetPower(TrainData tr, RealVector V) {
        final double Va   = V.getEntry(tr.a());
        final double Vb   = V.getEntry(tr.b());
        final double dV   = Va - Vb;
        final double vabs = Math.abs(dV);

        final double req   = tr.req_W();    // + motor, − regen
        final double vmin  = tr.vmin_V();   // motor cutoff (absorption below this is throttled)
        final double vmax  = tr.vmax_V();   // regen cutoff (above this is disconnected)
        final double imax  = tr.imax_A();   // current clamp

        double Iab; // current from a to b

        if (req >= 0.0) {
            // motor: throttle below vmin, be numerically safe near zero dV
            final double denom = Math.max(vabs, Math.max(vmin, 1e-6));
            final double scale = (vabs < vmin) ? Math.max(0.0, vabs / vmin) : 1.0;
            final double Ipos  = (req * scale) / denom;
            final double sgn   = (vabs < 1e-12) ? 1.0 : Math.signum(dV); // current flows high → low
            Iab = sgn * Ipos;
        } else {
            // regen: hard cutoff above vmax (no ramp)
            final double dVreg = (vabs < 1e-6) ? Math.copySign(1e-6, (dV == 0.0 ? 1.0 : dV)) : dV;
            final double vabs2 = Math.abs(dVreg);
            final double fracLine = (vabs2 <= vmax) ? 1.0 : 0.0;
            Iab = (req * fracLine) / dVreg; // req < 0 gives proper sign against dV
        }

        if (imax > 0.0 && Math.abs(Iab) > imax) {
            Iab = Math.copySign(imax, Iab);
        }

        // net power injected into the grid by this train
        return Iab * dV;
    }

    public static Double findTrainSignal(File out, String trainId, String signal) throws Exception {
        try (var stream = java.nio.file.Files.lines(out.toPath())) {
            return stream
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !s.startsWith("time_s,")) // header
                    .map(line -> line.split(",", -1))
                    .map(arr -> {
                        for (int i = 0; i < arr.length; i++) arr[i] = arr[i].trim();
                        return arr;
                    })
                    // columns: time_s,project,scenario,base_hash,object_type,object_id,signal,value,unit,stage,iter,note
                    .filter(arr -> arr.length >= 8)
                    .filter(arr -> "Train".equals(arr[4]))
                    .filter(arr -> trainId.equals(arr[5]))
                    .filter(arr -> signal.equals(arr[6]))
                    .map(arr -> {
                        try { return Double.parseDouble(arr[7]); }
                        catch (NumberFormatException e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
    }

    public static Double findNodeSignal(File out, String nodeId, String signal) throws Exception {
        try (var stream = java.nio.file.Files.lines(out.toPath())) {
            return stream
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !s.startsWith("time_s,")) // header
                    .map(line -> line.split(",", -1))
                    .map(arr -> {
                        for (int i = 0; i < arr.length; i++) arr[i] = arr[i].trim();
                        return arr;
                    })
                    // columns: ...,object_type,object_id,signal,value,...
                    .filter(arr -> arr.length >= 8)
                    .filter(arr -> "Node".equals(arr[4]))
                    .filter(arr -> nodeId.equals(arr[5]))
                    .filter(arr -> signal.equals(arr[6]))
                    .map(arr -> {
                        try { return Double.parseDouble(arr[7]); }
                        catch (NumberFormatException e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
    }

}
