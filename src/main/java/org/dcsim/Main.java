package org.dcsim;

import java.util.*;

public class Main {

    public record TrainSchedule(int id, boolean isUp, double startTime) {}

    public record TrainState(int trainId, boolean isUpDirection, double position, double speed, double power, double time) {}

    public static void main(String[] args) throws Exception {
        int upCount = 3;
        int downCount = 3;
        int headway = 300;
        double upStartTime = 0;
        double downStartTime = 600;
        double simStart = 0;
        double simEnd = 3600;
        double tickSeconds = 1.0;
        boolean useSI = true;

        double accel = 1.0;
        double cruiseSpeed = 22.22;
        double cruiseTime = 420;
        double brake = -1.0;
        double cruisePower = 200000.0;  // updated cruise power

        List<TrainSchedule> schedules = new ArrayList<>();
        for (int i = 0; i < upCount; i++)
            schedules.add(new TrainSchedule(i, true, upStartTime + i * headway));
        for (int i = 0; i < downCount; i++)
            schedules.add(new TrainSchedule(i + upCount, false, downStartTime + i * headway));

        Map<Integer, List<TrainState>> trainData = new LinkedHashMap<>();
        Map<Integer, TrainState> active = new HashMap<>();

        for (double t = simStart; t <= simEnd; t += tickSeconds) {
            for (TrainSchedule schedule : schedules) {
                if (Math.abs(t - schedule.startTime) < 0.001) {
                    double initialPos = schedule.isUp ? 0.0 : 10_000.0;
                    TrainState initial = new TrainState(schedule.id, schedule.isUp, initialPos, 0.0, 0.0, t);
                    active.put(schedule.id, initial);
                    trainData.put(schedule.id, new ArrayList<>(List.of(initial)));
                    System.out.printf("[%s] Train %d departs (%s)%n", TimeUtils.format((int)t), schedule.id, schedule.isUp ? "UP" : "DOWN");
                }
            }

            Iterator<Map.Entry<Integer, TrainState>> it = active.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                TrainState tr = e.getValue();
                double elapsed = t - tr.time;

                double newSpeed = 0.0;
                double newPower = 0.0;

                if (elapsed < cruiseSpeed / accel) {
                    newSpeed = accel * elapsed;
                    newPower = cruisePower;
                } else if (elapsed < cruiseSpeed / accel + cruiseTime) {
                    newSpeed = cruiseSpeed;
                    newPower = cruisePower;
                } else {
                    double tBraking = elapsed - cruiseSpeed / accel - cruiseTime;
                    newSpeed = cruiseSpeed + brake * tBraking;
                    if (newSpeed < 0) newSpeed = 0;
                    newPower = 0.0;
                }

                double delta = newSpeed * tickSeconds;
                double newPos = tr.isUpDirection() ? tr.position + delta : tr.position - delta;

                TrainState updated = new TrainState(tr.trainId, tr.isUpDirection, newPos, newSpeed, newPower, t);
                trainData.get(tr.trainId).add(updated);

                if (newSpeed == 0.0) {
                    it.remove();
                    System.out.printf("[%s] Train %d stopped%n", TimeUtils.format((int)t), tr.trainId);
                } else {
                    e.setValue(updated);
                }
            }

            if (active.isEmpty()) {
                System.out.printf("[%s] All trains stopped. Simulation ends.%n", TimeUtils.format((int)t));
                break;
            }
        }

        ExcelExport.exportTrainData("output.xlsx", trainData, useSI);
    }
}
