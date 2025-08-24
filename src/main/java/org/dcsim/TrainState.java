package org.dcsim;

import org.dcsim.utils.PositionUtils;

/**
 * Immutable snapshot of a train's state at a simulation instant.
 *
 * — Backward-compatible API —
 *  - position() still returns meters along the current line (same as before).
 *  - speed(), power(), time(), isUp(), startTime() unchanged.
 *
 * — New multi-line fields —
 *  - lineId: which line the train is currently on.
 *  - getLineId() accessor.
 *  - formattedPosition(): "line km+mmm" string for logs/exports.
 */
public final class TrainState {

    // Identity & timing
    public final int id;
    public final double time;
    public final int startTime;

    // Track & kinematics
    public final int lineId;            // NEW: current line identifier
    public final boolean isUp;          // direction flag (kept)
    public final double positionMeters; // NEW: meters along the line (replaces old 'position')
    public final double speed;          // m/s

    // Electrical demand (+ = motoring draw, − = regen)
    public final double power;          // W (requested or delivered, depending on context)

    public TrainState(
            int id,
            boolean isUp,
            int lineId,
            double positionMeters,
            double speed,
            double power,
            double time,
            int startTime
    ) {
        this.id = id;
        this.isUp = isUp;
        this.lineId = lineId;
        this.positionMeters = positionMeters;
        this.speed = speed;
        this.power = power;
        this.time = time;
        this.startTime = startTime;
    }

    // -------- Backward-compatible accessors (same names/signatures as before) --------

    public double time()         { return time; }
    /** Returns meters along the current line (same semantic as old 'position'). */
    public double position()     { return positionMeters; }
    public double speed()        { return speed; }
    public double power()        { return power; }
    public boolean isUp()        { return isUp; }
    public int startTime()       { return startTime; }

    // -------- New accessors --------

    public int getLineId()       { return lineId; }
    public double getPositionMeters() { return positionMeters; }

    /** Human-friendly "line km+mmm" (e.g., "1 12+340"). */
    public String formattedPosition() {
        return PositionUtils.format(lineId, positionMeters);
    }

    // -------- "With" helpers for functional updates --------

    public TrainState withPosition(int newLineId, double newMeters) {
        return new TrainState(id, isUp, newLineId, newMeters, speed, power, time, startTime);
    }

    public TrainState withAdvancedPosition(double deltaMeters) {
        return new TrainState(id, isUp, lineId, positionMeters + deltaMeters, speed, power, time, startTime);
    }

    public TrainState withSpeed(double newSpeed) {
        return new TrainState(id, isUp, lineId, positionMeters, newSpeed, power, time, startTime);
    }

    public TrainState withPower(double newPower) {
        return new TrainState(id, isUp, lineId, positionMeters, speed, newPower, time, startTime);
    }

    public TrainState atTime(double newTime) {
        return new TrainState(id, isUp, lineId, positionMeters, speed, power, newTime, startTime);
    }

    public TrainState flipDirection() {
        return new TrainState(id, !isUp, lineId, positionMeters, speed, power, time, startTime);
    }
}
