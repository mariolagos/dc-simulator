package org.supply.track;

/**
 * Track calibration point used when converting a run profile position to an
 * absolute railway position.
 */
public final class TrackInterpolationPoint {
    public final double positionM;
    public final int bisKm;
    public final double bisMeter;

    public TrackInterpolationPoint(double positionM, int bisKm, double bisMeter) {
        this.positionM = positionM;
        this.bisKm = bisKm;
        this.bisMeter = bisMeter;
    }

    public double absolutePositionM() {
        return bisKm * 1000.0 + bisMeter;
    }

    @Override
    public String toString() {
        return "TrackInterpolationPoint{" +
                "positionM=" + positionM +
                ", bisKm=" + bisKm +
                ", bisMeter=" + bisMeter +
                '}';
    }
}
