package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.*;

public final class GridResult {

    // ---- device/node snapshots ----
    private final Map<String, Real> devicePowers          = new LinkedHashMap<>();
    private final Map<String, Real> deviceCurrents        = new LinkedHashMap<>();
    private final Map<String, Real> deviceRequestedPowers = new LinkedHashMap<>();
    private final Map<String, Real> nodeVoltages         = new LinkedHashMap<>();

    // (optional) matrices used for debugging / reconstruction
    private final RealMatrix Y;
    private final RealVector J;

    // headline totals for writer/plots
    private Totals totals;

    public GridResult(RealMatrix Y, RealVector J) {
        this.Y = Y;
        this.J = J;
    }

    // ---- writes from solver/actor ----
    public void setCurrent(String deviceId, Real current)          { deviceCurrents.put(deviceId, current); }
    public void setPower(String deviceId, Real power)              { devicePowers.put(deviceId, power); }
    public void setRequestedPower(String deviceId, Real p)         { deviceRequestedPowers.put(deviceId, p); }
    public void setVoltage(String nodeId, Real voltage)               { nodeVoltages.put(nodeId, voltage); }

    // ---- reads for writer/plots ----
    public Real getPower(String deviceId)              { return devicePowers.get(deviceId); }
    public Real getCurrent(String deviceId)            { return deviceCurrents.get(deviceId); }
    public Real getRequestedPower(String deviceId)     { return deviceRequestedPowers.get(deviceId); }

    // handy null-safe doubles (optional; use if you like)
    public double getPowerAsDouble(String id)          { return asDouble(getPower(id)); }
    public double getRequestedPowerAsDouble(String id) { return asDouble(getRequestedPower(id)); }
    public double getVoltageAsDouble(String nodeId)       { return asDouble(getLatestNodeVoltage(nodeId)); }
    private static double asDouble(Real r)             { return (r == null) ? 0.0 : r.asDouble(); }

    // matrices (may be null)
    public RealMatrix getYMatrix() { return Y; }
    public RealVector getJVector() { return J; }

    // ---- headline totals ----
    public void setTotals(Totals t)  { this.totals = t; }
    public void addTotals(Totals t)  { this.totals = t; }   // alias for your current call site
    public Totals getTotals()        { return totals; }

    // ----------- Back-compat aliases (older code paths call these) -----------
    public Real getLatestDevicePower(String deviceId)      { return getPower(deviceId); }
    public Real getLatestDeviceCurrent(String deviceId)    { return getCurrent(deviceId); }
    public Real getLatestRequestedPower(String deviceId)   { return getRequestedPower(deviceId); }
    public Real getLatestNodeVoltage(String nodeId)        { return nodeVoltages.getOrDefault(nodeId, Real.ZERO); }

    public void setLatestDevicePower(String deviceId, Real p)    { setPower(deviceId, p); }
    public void setLatestDeviceCurrent(String deviceId, Real i)  { setCurrent(deviceId, i); }
    public void setLatestRequestedPower(String deviceId, Real p) { setRequestedPower(deviceId, p); }
    public void setLatestNodeVoltage(String nodeId, Real v)         { setVoltage(nodeId, v); }

    // ---- legacy/compat aliases expected by ResultCsvWriter ----
    public Real getLatestDeviceRequestedPower(String deviceId)    { return deviceRequestedPowers.get(deviceId); }

    public Real getVoltage(String nodeId)                            { return getLatestNodeVoltage(nodeId); } // alias


    // immutable value carrier for “summa-summarum"
    public static final class Totals {
        public final double pStations;         // Σ substation P_out (to network)
        public double pTrains;           // Σ train line-side power (signed)
        public final double pLineLoss;         // Σ I^2R ≥ 0
        public final double pBrake;            // Σ brake resistor ≥ 0
        public final double pReqTrains;        // Σ requested train power (signed)
        public final double pMismatch;         // pStations − (pTrains + pLineLoss)
        public final double pUndersupply;      // max(0, (pTrains + pLineLoss) − pStations)
        public final double pUnderreceptivity; // max(0, pBrake)

        public Totals(double pStations,
                      double pTrains,
                      double pLineLoss,
                      double pBrake,
                      double pReqTrains,
                      double pMismatch,
                      double pUndersupply,
                      double pUnderreceptivity) {
            this.pStations         = pStations;
            this.pTrains           = pTrains;
            this.pLineLoss         = pLineLoss;
            this.pBrake            = pBrake;
            this.pReqTrains        = pReqTrains;
            this.pMismatch         = pMismatch;
            this.pUndersupply      = pUndersupply;
            this.pUnderreceptivity = pUnderreceptivity;
        }
    }
}
