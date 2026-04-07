package org.dcsim.sim;

import org.dcsim.electric.AnchorStamp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TrainAnchorComponent {

    // Train + routing state
    private final String anchorNodeId;   // fixed node_id for the anchor
    private final List<EdgeRef> path;    // ordered edge list (head -> tail)
    private int edgeIndex;               // current edge
    private double xM;                   // local position on current edge [m]
    private double vMS;                  // m/s (informational; movement controlled externally)
    private double pW;                   // +traction / -regen
    private double VaPrev = 1000.0;      // initial voltage guess

    // Solver-side mapping
    private int anchorIndex = -1;        // matrix/vector index for anchor node

    // Numerics
    private final double Rmin;           // e.g. 1e-6
    private final double epsFrac;        // e.g. 1e-4..1e-3
    private final double epsAbsM = 0.1;  // absolute min margin for stable split

    // Prefix sums along route for fast s -> (edge,x) mapping
    private double[] cumLen;             // cumLen[k] = length before edge k
    private double totalLen;

    // Mild under-relaxation for CP load linearization point
    private final double vaBlend = 0.25; // 0<beta<=1; 1.0 = no damping

    public TrainAnchorComponent(
            String anchorNodeId,
            List<EdgeRef> path,
            double vMS,
            double pW,
            double Rmin,
            double epsFrac
    ) {
        this.anchorNodeId = Objects.requireNonNull(anchorNodeId);
        this.path = Objects.requireNonNull(path);
        if (path.isEmpty()) throw new IllegalArgumentException("path is empty");

        this.vMS = vMS;
        this.pW = pW;
        this.Rmin = Rmin;
        this.epsFrac = epsFrac;

        buildCum();
        this.edgeIndex = 0;

        double L0 = Math.max(1e-9, this.path.get(0).lengthM);
        double eps0 = Math.max(epsAbsM, this.epsFrac * L0);
        this.xM = eps0;
    }

    private void buildCum() {
        cumLen = new double[path.size() + 1];
        cumLen[0] = 0.0;
        for (int i = 0; i < path.size(); i++) {
            cumLen[i + 1] = cumLen[i] + Math.max(1e-9, path.get(i).lengthM);
        }
        totalLen = cumLen[path.size()];
    }

    /** Initialize to a given edge and local x; clamped to margins. */
    public void resetAt(int edge, double xM) {
        this.edgeIndex = Math.max(0, Math.min(edge, path.size() - 1));
        double L = Math.max(1e-9, path.get(this.edgeIndex).lengthM);
        double eps = Math.max(epsAbsM, epsFrac * L);
        this.xM = Math.max(eps, Math.min(xM, L - eps));
    }

    /** Set absolute progress s [m] along whole route (0..totalLen). */
    public void setAbsoluteProgressM(double sMeters) {
        if (cumLen == null) buildCum();
        double s = Math.max(0.0, Math.min(sMeters, totalLen));

        int lo = 0, hi = path.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumLen[mid + 1] <= s) lo = mid + 1;
            else hi = mid;
        }
        edgeIndex = Math.min(lo, path.size() - 1);

        double L = Math.max(1e-9, path.get(edgeIndex).lengthM);
        double local = s - cumLen[edgeIndex];
        double eps = Math.max(epsAbsM, epsFrac * L);
        xM = Math.max(eps, Math.min(local, L - eps));
    }

    /** Set explicit edge index + local x [m]; clamped to margins. */
    public void setEdgeAndX(int edge, double xMeters) {
        edgeIndex = Math.max(0, Math.min(edge, path.size() - 1));
        double L = Math.max(1e-9, path.get(edgeIndex).lengthM);
        double eps = Math.max(epsAbsM, epsFrac * L);
        xM = Math.max(eps, Math.min(xMeters, L - eps));
    }

    /**
     * Stamp ONLY the active split + train load.
     * Base network is stamped elsewhere by the solver.
     */
    public void stamp(
            AnchorStamp.AdmittanceMatrix Y,
            AnchorStamp.CurrentVector J,
            Map<String, Integer> nodeIndex
    ) {
        EdgeRef cur = path.get(edgeIndex);
        double L = Math.max(1e-12, cur.lengthM);
        double rPerM = cur.R / L;

        Integer iIdx = nodeIndex.get(cur.i);
        Integer aIdx = nodeIndex.get(anchorNodeId);
        Integer jIdx = nodeIndex.get(cur.j);

        if (iIdx == null) {
            throw new IllegalStateException("Missing node index for edge start: " + cur.i);
        }
        if (aIdx == null) {
            throw new IllegalStateException("Missing node index for anchor node: " + anchorNodeId);
        }
        if (jIdx == null) {
            throw new IllegalStateException("Missing node index for edge end: " + cur.j);
        }

        this.anchorIndex = aIdx;

        // i --(Rleft)-- a --(Rright)-- j
        AnchorStamp.stampAnchorSplit(Y, iIdx, aIdx, jIdx, rPerM, L, xM, Rmin);

        // Train Norton-equivalent load at anchor node
        AnchorStamp.stampConstantPLoad(Y, J, aIdx, pW, VaPrev, null);
    }

    /** Called after solve; updates VaPrev with mild damping for stability. */
    public void afterSolve(double[] V) {
        double VaNew = (anchorIndex >= 0 && anchorIndex < V.length) ? V[anchorIndex] : VaPrev;
        this.VaPrev = this.VaPrev + vaBlend * (VaNew - this.VaPrev);
    }

    /** Movement is externally controlled now; kept as no-op for compatibility. */
    public void advance(double dt) {
        // intentionally empty
    }

    public int getEdgeIndex() {
        return edgeIndex;
    }

    public double getXM() {
        return xM;
    }

    public double getVaPrev() {
        return VaPrev;
    }

    public String getAnchorNodeId() {
        return anchorNodeId;
    }

    public int getAnchorIndex() {
        return anchorIndex;
    }

    public void setSpeedMS(double vMS) {
        this.vMS = vMS;
    }

    public double getSpeedMS() {
        return vMS;
    }

    public EdgeRef currentEdge() {
        return path.get(edgeIndex);
    }

    public double getPowerW() {
        return pW;
    }

    public double getCurrentA() {
        return (VaPrev > 1e-9) ? (pW / VaPrev) : 0.0;
    }

    public double getTotalLengthM() {
        return totalLen;
    }
}