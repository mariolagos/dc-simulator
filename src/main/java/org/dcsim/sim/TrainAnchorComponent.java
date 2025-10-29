package org.dcsim.sim;

import org.dcsim.electric.AnchorStamp;

import java.util.List;
import java.util.Objects;

public final class TrainAnchorComponent {

    // Train + routing state
    private final int anchorNode;       // fast node-id för ankaret (ändras aldrig)
    private final List<EdgeRef> path;   // ordnad lista av kanter (head->tail)
    private int edgeIndex;              // aktuell kant
    private double xM;                  // position på aktuell kant [m]
    private double vMS;                 // m/s (informativt/logg, rörelse styrs externt)
    private double pW;                  // +drag / -regen (kan vara konstant tills vidare)
    private double VaPrev = 1000.0;     // startgissning, DC-nivå

    // Numerik
    private final double Rmin;          // t.ex. 1e-6
    private final double epsFrac;       // t.ex. 1e-4..1e-3 (fraktion av L)
    private final double epsAbsM = 0.1; // absolut minmarginal (0.1 m) för stabil split

    // Prefixsummor längs rutten för snabb s->(edge,x)-mappning
    private double[] cumLen;            // cumLen[k] = längd före kant k
    private double totalLen;

    // Mild under-relaxering för CP-lastens lineariseringspunkt (jämnar ut)
    private final double vaBlend = 0.25; // 0<beta<=1; 1.0 = ingen dämpning

    // ---------- livscykel ----------
    public TrainAnchorComponent(
            int anchorNode,
            List<EdgeRef> path,
            double vMS,
            double pW,
            double Rmin,
            double epsFrac
    ) {
        this.anchorNode = anchorNode;
        this.path = Objects.requireNonNull(path);
        if (path.isEmpty()) throw new IllegalArgumentException("path is empty");
        this.vMS = vMS;
        this.pW = pW;
        this.Rmin = Rmin;
        this.epsFrac = epsFrac;

        buildCum();                     // beräkna cumulerad längd
        this.edgeIndex = 0;
        double L0 = Math.max(1e-9, this.path.get(0).lengthM);
        double eps0 = Math.max(epsAbsM, this.epsFrac * L0);
        this.xM = eps0;                 // börja strax från vänster ände
    }

    // Bygg prefixsummor
    private void buildCum() {
        cumLen = new double[path.size() + 1];
        cumLen[0] = 0.0;
        for (int i = 0; i < path.size(); i++) {
            cumLen[i + 1] = cumLen[i] + Math.max(1e-9, path.get(i).lengthM);
        }
        totalLen = cumLen[path.size()];
    }

    /** Initiera till given kant och lokalt x (klampas till marginaler). */
    public void resetAt(int edge, double xM) {
        this.edgeIndex = Math.max(0, Math.min(edge, path.size() - 1));
        double L = Math.max(1e-9, path.get(this.edgeIndex).lengthM);
        double eps = Math.max(epsAbsM, epsFrac * L);
        this.xM = Math.max(eps, Math.min(xM, L - eps));
    }

    // ---------- extern styrning av position ----------
    /** Sätt absolut progress s [m] längs hela rutten (0..totalLen): mappas till (edge,x). */
    public void setAbsoluteProgressM(double sMeters) {
        if (cumLen == null) buildCum();
        double s = Math.max(0.0, Math.min(sMeters, totalLen));

        // binärsök vilken kant s hamnar i
        int lo = 0, hi = path.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumLen[mid + 1] <= s) lo = mid + 1; else hi = mid;
        }
        edgeIndex = Math.min(lo, path.size() - 1);

        double L = Math.max(1e-9, path.get(edgeIndex).lengthM);
        double local = s - cumLen[edgeIndex];
        double eps = Math.max(epsAbsM, epsFrac * L);
        xM = Math.max(eps, Math.min(local, L - eps));
    }

    /** Sätt explicit kantindex + lokalt x [m] (klampas till marginaler). */
    public void setEdgeAndX(int edge, double xMeters) {
        edgeIndex = Math.max(0, Math.min(edge, path.size() - 1));
        double L = Math.max(1e-9, path.get(edgeIndex).lengthM);
        double eps = Math.max(epsAbsM, epsFrac * L);
        xM = Math.max(eps, Math.min(xMeters, L - eps));
    }

    // ---------- stämpling & feedback ----------
    /** Stämpla ENDAST aktiv split + tåglast (solver stämplar basnätet osplittat). */
    public void stamp(AnchorStamp.AdmittanceMatrix Y, AnchorStamp.CurrentVector J) {
        EdgeRef cur = path.get(edgeIndex);
        double L = Math.max(1e-12, cur.lengthM);
        double rPerM = cur.R / L; // total Ω -> Ω/m för split

        // i --(Rleft)-- anchorNode --(Rright)-- j
        AnchorStamp.stampAnchorSplit(Y, cur.i, anchorNode, cur.j, rPerM, L, xM, Rmin);

        // Tåglast (Norton-ekvivalent). Här: konstant-P mot VaPrev.
        AnchorStamp.stampConstantPLoad(Y, J, anchorNode, pW, VaPrev, null);
    }

    /** Anropas efter lösning; uppdatera VaPrev (med mild dämpning för stabilitet). */
    public void afterSolve(double[] V) {
        double VaNew = (anchorNode >= 0 && anchorNode < V.length) ? V[anchorNode] : VaPrev;
        // under-relaxering för att undvika “kamtänder" i CP-lineariserad last
        this.VaPrev = this.VaPrev + vaBlend * (VaNew - this.VaPrev);
    }

    /** Rörelse styrs externt numera; no-op för kompatibilitet. */
    public void advance(double dt) {
        // avsiktligt tom – behålls för bakåtkompatibilitet med existerande anrop
    }

    // ---------- getters (logg/visning) ----------
    public int getEdgeIndex() { return edgeIndex; }
    public double getXM() { return xM; }
    public double getVaPrev() { return VaPrev; }
    public int getAnchorNode() { return anchorNode; }
    public void setSpeedMS(double vMS) { this.vMS = vMS; }
    public double getSpeedMS() { return vMS; }
    public EdgeRef currentEdge() { return path.get(edgeIndex); }
    public double getPowerW() { return pW; }
    public double getCurrentA() { return (VaPrev > 1e-9) ? (pW / VaPrev) : 0.0; }


    // ---------- hjälpinfo ----------
    public double getTotalLengthM() { return totalLen; }
}
