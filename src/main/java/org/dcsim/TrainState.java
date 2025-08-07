
package org.dcsim;

public class TrainState {
    public final int id;
    public final boolean isUp;
    public final double position;
    public final double speed;
    public final double power;
    public final double time;
    public final int startTime;  // ߆ Added start time

    public TrainState(int id, boolean isUp, double position, double speed, double power, double time, int startTime) {
        this.id = id;
        this.isUp = isUp;
        this.position = position;
        this.speed = speed;
        this.power = power;
        this.time = time;
        this.startTime = startTime;
    }

    public double time() {
        return time;
    }

    public double position() {
        return position;
    }

    public double speed() {
        return speed;
    }

    public double power() {
        return power;
    }

    public boolean isUp() {
        return isUp;
    }

    public int startTime() {
        return startTime;
    }
}
