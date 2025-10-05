package org.dcsim.sim;

/** Simple train runtime state for one anchor. */
public final class TrainRuntime {
    public int edgeIndex;        // which edge on the path
    public double xM;            // position along current edge [0..L]
    public final int anchorNode; // node id 'a' (kept constant across migration)
    public double vMS;           // speed along track (m/s), placeholder
    public double pW;            // constant P load (W), placeholder
    public double VaPrev = 1000.0; // previous Va (start guess), avoid div-by-zero

    public TrainRuntime(int anchorNode, double vMS, double pW) {
        this.anchorNode = anchorNode;
        this.vMS = vMS;
        this.pW = pW;
    }
}
