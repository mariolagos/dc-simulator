package org.dcsim.sim;

/** Minimal edge-referens med total R och längd L (från GridModelLoader). */
public final class EdgeRef {
    public final String i, j;          // nodindex
    public final double R;          // totalresistans (Ohm)
    public final double lengthM;    // meter (för split)
    public EdgeRef(String i, String j, double R, double lengthM) {
        this.i = i; this.j = j; this.R = R; this.lengthM = lengthM;
    }
}
