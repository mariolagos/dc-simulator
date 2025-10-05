// src/test/java/org/dcsim/it/TestSupport.java
package org.dcsim.it;

import org.dcsim.electric.*;
import org.dcsim.math.Real;

import java.util.*;

public final class TestSupport {

    private TestSupport() {}

    /** Bygger en minimal 3-noder grid: SS0 — Nmid — SS1, med tåget på Nmid. */
    public static GridModel<Real> buildMiniGrid(boolean allowBackfeed, double emfV) {
        GridModel<Real> m = new GridModel<>();
        int GND = 0, N0 = 1, NMID = 2, N1 = 3;
        m.addNode(GND); m.setGroundNodeId(GND);
        m.addNode(N0);  m.addNode(NMID); m.addNode(N1);

        // Två likriktarstationer med internresistans (ohmig källa).
        Substation ss0 = new Substation("SS0", N0, Real.fromDouble(emfV), Real.fromDouble(0.01));
        ss0.setAllowBackfeed(allowBackfeed); // TODO: om din klass använder annat namn/flagga — anpassa
        Substation ss1 = new Substation("SS1", N1, Real.fromDouble(emfV), Real.fromDouble(0.01));
        ss1.setAllowBackfeed(allowBackfeed);

        m.addDevice(ss0);
        m.addDevice(ss1);

        // Två linor N0—NMID—N1 (1.5 km vardera, 0.03 ohm/km)
        m.addDevice(new Line("L0", N0, NMID, 1500.0, Real.fromDouble(0.03)));
        m.addDevice(new Line("L1", NMID, N1, 1500.0, Real.fromDouble(0.03)));

        // Tåg-”device” på mittennoden (profilstyrt via setRequestedComponents)
        TrainLoad t = new TrainLoad("T1", NMID);
        t.setMaxVoltage(Real.fromDouble(900.0)); // Vmax
        t.setCutoffVoltage(Real.fromDouble(880.0)); // Vmin
        // ingen strömbegränsning för test
        m.addDevice(t);

        return m;
    }

    /** Hämtar tåget T1 ur modellen (kastar om det saknas). */
    public static TrainLoad train(GridModel<Real> m) {
        Device<Real> d = m.getDevice("T1");
        if (!(d instanceof TrainLoad)) throw new IllegalStateException("Train T1 not found");
        return (TrainLoad) d;
    }

    /** Sätter begäran för T1 (kW): motordrift (+), regen (negativ brakingKW), eller ohmisk broms (+brakingKW). */
    public static void requestTrain(TrainLoad t1, double motoringKW, double brakingKW, double auxKW) {
        // TODO: anpassa om din signatur skiljer: (motoringKW, brakingKW, auxiliaryKW)
        t1.setRequestedComponents(motoringKW, brakingKW, auxKW);
    }

    // ===== Diod-iteration (samma idé som i GridModelActor) =====
    public static GridResult solveWithRectifierBlocks(GridModel<Real> model, DcElectricSolver solver,
                                                      double timeSec, int step) throws Exception {
        final double BF_EPS_A = 1e-6;
        final int MAX_IT = 3;

        // samla SS + originalparametrar
        class Orig { final double emf, r; Orig(double e, double r){this.emf=e; this.r=r;} }
        Map<String, Orig> orig = new HashMap<>();
        List<Substation> all = new ArrayList<>();
        for (Object idObj : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(String.valueOf(idObj));
            if (d instanceof Substation ss) {
                all.add(ss);
                orig.put(ss.getId(), new Orig(ss.getEmf().asDouble(), ss.getInternalResistance().asDouble()));
            }
        }

        GridResult res = null;
        for (int it = 0; it < MAX_IT; it++) {
            res = solver.solve(model, timeSec, step);

            boolean changed = false;
            for (Substation ss : all) {
                if (ss.isAllowBackfeed()) continue; // tillåter backfeed → blockera ej

                Real iR = res.getLatestDeviceCurrent(ss.getId());
                double i = (iR != null) ? iR.asDouble() : 0.0;
                // backfeed in i stationen? (tecken enl. dina konventioner)
                if (i < -BF_EPS_A) {
                    ss.setInternalResistance(Real.fromDouble(1e12));
                    ss.setEmf(Real.fromDouble(0.0));
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // återställ
        for (Substation ss : all) {
            Orig o = orig.get(ss.getId());
            ss.setInternalResistance(Real.fromDouble(o.r));
            ss.setEmf(Real.fromDouble(o.emf));
        }
        return res;
    }

    public static double powW(GridResult res, String devId) {
        Real p = res.getLatestDevicePower(devId);
        return p == null ? 0.0 : p.asDouble();
    }
    public static double curA(GridResult res, String devId) {
        Real i = res.getLatestDeviceCurrent(devId);
        return i == null ? 0.0 : i.asDouble();
    }
    public static double nodeV(GridResult res, int nodeId) {
        Real v = res.getLatestNodeVoltage(nodeId);
        return v == null ? 0.0 : v.asDouble();
    }
}
