package org.dcsim.power;

public final class ProfileSample {
    public final double tSec;
    public final double positionMeters;
    public final double motoringKW;    // >= 0
    public final double brakingKW;     // <= 0 (redan teckensatt)
    public final double auxiliaryKW;   // >= 0
    public final double requestedTotalW; // (motoring + braking + auxiliary) * 1000

    public ProfileSample(double tSec, double positionMeters,
                         double motoringKW, double brakingKW, double auxiliaryKW) {
        this.tSec = tSec;
        this.positionMeters = positionMeters;
        this.motoringKW = motoringKW;
        this.brakingKW = brakingKW;
        this.auxiliaryKW = auxiliaryKW;
        this.requestedTotalW = (motoringKW + brakingKW + auxiliaryKW) * 1000.0;
    }
}
