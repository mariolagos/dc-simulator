package org.dcsim.solver.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal resultatcontainer för DC-lösningen.
 * - V: nodspänningar i kompakt indexordning (0..n-1).
 * - I/P: ström och effekt per device-id (samma id som i LineData/SubstationData/TrainData).
 *
 * Konvention: Effekt P = I * (Va - Vb) (positiv => upptag från nätet, negativ => matning till nätet).
 */
public final class DcSolveResult {

    /** Noder: spänning per kompaktindex. */
    public final double[] V;

    private final Map<String, Double> IbyId = new HashMap<>();
    private final Map<String, Double> PbyId = new HashMap<>();

    public DcSolveResult(int n) {
        this.V = new double[n];
    }

    // --- Sättare som används av solvern ---

    public void setLine(String id, double Iab, double P) {
        IbyId.put(id, Iab);
        PbyId.put(id, P);
    }

    public void setSubstation(String id, double I, double P) {
        IbyId.put(id, I);
        PbyId.put(id, P);
    }

    public void setTrain(String id, double I, double P) {
        IbyId.put(id, I);
        PbyId.put(id, P);
    }

    // --- Hämtare för tester/diagnostik ---

    /** Hämtar strömmen (A) för device med givet id, eller null om okänt. */
    public Double getDeviceCurrent(String id) {
        return IbyId.get(id);
    }

    /** Hämtar effekten (W) för device med givet id, eller null om okänt. */
    public Double getDevicePower(String id) {
        return PbyId.get(id);
    }

    /** Hela vektor av nodspänningar (kompaktindex). */
    public double[] getVoltages() {
        return V;
    }

    /** Spänning för nod kompaktindex i (0..n-1). */
    public double getVoltageAt(int i) {
        return V[i];
    }
}
