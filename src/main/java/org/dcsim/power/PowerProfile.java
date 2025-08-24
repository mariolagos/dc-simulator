package org.dcsim.power;

import org.dcsim.PowerPoint;
import org.dcsim.math.Real;

import java.util.List;

public class PowerProfile {

    private final List<PowerPoint> points;

    public PowerProfile(List<PowerPoint> points) {
        this.points = points;
    }

    /**
     * Computes the current I = P / U based on interpolated power and given voltage.
     */
    public Real computeCurrent(Real voltage, double time) {
        if (points.isEmpty() || voltage.isZero()) return Real.ZERO;

        Real interpolatedPower = getPowerAtTime(time);
        return interpolatedPower.divide(voltage);
    }

    /**
     * Returns interpolated power at a given time (Real).
     */
    public Real getPowerAtTime(double time) {
        if (points.isEmpty()) return Real.ZERO;

        if (time <= points.get(0).time()) {
            return Real.fromDouble(points.get(0).power());
        }

        if (time >= points.get(points.size() - 1).time()) {
            return Real.fromDouble(points.get(points.size() - 1).power());
        }

        for (int i = 0; i < points.size() - 1; i++) {
            PowerPoint p1 = points.get(i);
            PowerPoint p2 = points.get(i + 1);
            if (p1.time() <= time && time <= p2.time()) {
                double fraction = (time - p1.time()) / (p2.time() - p1.time());
                double interpolated = p1.power() + fraction * (p2.power() - p1.power());
//                System.out.printf("Looking up power at t=%.1f, points available: %d%n", time, points.size());
//                points.forEach(p -> System.out.printf("  point @ t=%.1f, P=%.1f%n", p.time(), p.power()));
                return Real.fromDouble(interpolated);
            }
        }



        return Real.ZERO; // Should never reach
    }

    /**
     * Returns raw data points.
     */
    public List<PowerPoint> getPoints() {
        return points;
    }

    /**
     * Alias for getPowerAtTime.
     */
    public Real interpolatePower(double time) {
        return getPowerAtTime(time);
    }

    /**
     * Finds and returns the closest raw PowerPoint to a given time.
     */
    public PowerPoint getNearestPoint(double time) {
        PowerPoint closest = points.get(0);
        double minDiff = Math.abs(time - closest.time());

        for (PowerPoint p : points) {
            double diff = Math.abs(time - p.time());
            if (diff < minDiff) {
                closest = p;
                minDiff = diff;
            }
        }

        return closest;
    }

    /**
     * Returns the start time of the power profile.
     */
    public double getStartTime() {
        return points.isEmpty() ? 0.0 : points.get(0).time();
    }

    /**
     * Returns the end time of the power profile.
     */
    public double getEndTime() {
        return points.isEmpty() ? 0.0 : points.get(points.size() - 1).time();
    }

    @Override
    public String toString() {
        return "PowerProfile{" +
                "points=" + points +
                '}';
    }
}
