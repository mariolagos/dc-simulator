package org.dcsim.sim;

/** Edge parameters used by the runtime loop. */
public final class EdgeProps {
    public final int i, j;           // node indices for this edge
    public final double rPerM;       // Ohm/m
    public final double lengthM;     // m
    public EdgeProps(int i, int j, double rPerM, double lengthM) {
        this.i=i; this.j=j; this.rPerM=rPerM; this.lengthM=lengthM;
    }
}
