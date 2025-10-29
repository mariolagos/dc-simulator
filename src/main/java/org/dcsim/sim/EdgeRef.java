package org.dcsim.sim;

/** Minimal edge-referens med total R och längd L (från GridModelLoader). */
public final class EdgeRef {
    public final int i, j;          // nodindex
    public final double R;          // totalresistans (Ohm)
    public final double lengthM;    // meter (för split)
    public EdgeRef(int i, int j, double R, double lengthM) {
        this.i = i; this.j = j; this.R = R; this.lengthM = lengthM;
    }
}
