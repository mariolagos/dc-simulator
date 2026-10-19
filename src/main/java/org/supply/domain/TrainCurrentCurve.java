package org.supply.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable traction-current envelope, piecewise linear in terminal voltage. */
public record TrainCurrentCurve(List<Point> points) {
    public record Point(double voltageV, double currentA) { }

    public TrainCurrentCurve {
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (points.size() < 2) throw new IllegalArgumentException("Current curve requires at least two points");
        double previous = Double.NEGATIVE_INFINITY, maximum = 0;
        for (Point p : points) {
            if (!Double.isFinite(p.voltageV()) || p.voltageV() <= 0 || p.voltageV() <= previous)
                throw new IllegalArgumentException("Current curve voltages must be finite, positive and strictly increasing");
            if (!Double.isFinite(p.currentA()) || p.currentA() < 0)
                throw new IllegalArgumentException("Current curve currents must be finite and non-negative");
            previous = p.voltageV(); maximum = Math.max(maximum, p.currentA());
        }
        if (maximum <= 0) throw new IllegalArgumentException("Current curve must contain a positive current limit");
    }

    public double currentLimitA(double voltageV) {
        if (!Double.isFinite(voltageV)) throw new IllegalArgumentException("Terminal voltage must be finite");
        if (voltageV <= points.get(0).voltageV()) return points.get(0).currentA();
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i - 1), b = points.get(i);
            if (voltageV <= b.voltageV()) {
                double fraction = (voltageV - a.voltageV()) / (b.voltageV() - a.voltageV());
                return a.currentA() + fraction * (b.currentA() - a.currentA());
            }
        }
        return points.get(points.size() - 1).currentA();
    }

    public double maximumA() { return points.stream().mapToDouble(Point::currentA).max().orElseThrow(); }
    /** Comma-free representation for the existing longtable CSV writer. */
    public String describe() {
        return points.stream().map(p -> p.voltageV() + ":" + p.currentA()).collect(Collectors.joining(";"));
    }
}
