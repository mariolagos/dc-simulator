// src/test/java/org/dcsim/grid/DummyPort.java
package org.dcsim.grid;

public final class DummyPort implements PowerPort {
    private double v;
    private double p;

    public DummyPort(double initialVoltage) { this.v = initialVoltage; }

    @Override public double getVoltage() { return v; }
    public void setVoltage(double v) { this.v = v; }

    @Override public void setRequestedPower(double watts) { this.p = watts; }
    @Override public double getRequestedPower() { return p; }

    @Override public int getNodeId() { return -1; }
}
