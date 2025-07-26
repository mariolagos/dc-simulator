package org.dcsim;

public class TrainSchedule {
    public final int id;
    public final boolean isUp;
    public final int startTime;

    public TrainSchedule(int id, boolean isUp, int startTime) {
        this.id = id;
        this.isUp = isUp;
        this.startTime = startTime;
    }
}
