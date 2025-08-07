package org.dcsim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
        boolean useSI = true;
        int tick = 1;
        int simEnd = 7200;

        int upTrainCount = 5;
        int downTrainCount = 5;
        int upStartTime = 0;
        int downStartTime = 0;
        int upHeadway = 300;
        int downHeadway = 300;

        double cruiseSpeed = 22.22;
        double accel = 1.0;
        double braking = -1.0;
        double cruisePower = 200_000;
        double tractionPower = 1_200_000;
        double brakingPower = -250_000;

        int trainId = 0;
        List<Train> trains = new ArrayList<>();

        for (int i = 0; i < upTrainCount; i++)
            trains.add(new Train(trainId++, true, upStartTime + i * upHeadway));
        for (int i = 0; i < downTrainCount; i++)
            trains.add(new Train(trainId++, false, downStartTime + i * downHeadway));

        Map<Integer, List<double[]>> trainData = new LinkedHashMap<>();
        for (Train train : trains)
            trainData.put(train.id, new ArrayList<>());

        List<Train> active = new ArrayList<>();

        for (int t = 0; t <= simEnd; t += tick) {
            for (Train train : trains) {
                if (train.startTime == t) {
                    train.init();
                    active.add(train);
                    System.out.println("Train " + train.id + " starts at " + t);
                }
            }

            List<Train> next = new ArrayList<>();

            for (Train train : active) {
                double pos = train.position;
                double speed = train.speed;
                double power;

                double stopPos = train.isUp ? 9310.0 : 0.0;
                double distToStop = Math.abs(stopPos - pos);
                double brakeDist = speed * speed / (2 * -braking);

                if (speed == 0 && t == train.startTime) {
                    speed = accel * tick;
                    power = tractionPower;
                } else if (speed < cruiseSpeed) {
                    speed = Math.min(speed + accel * tick, cruiseSpeed);
                    power = tractionPower;
                } else if (distToStop <= brakeDist) {
                    speed = Math.max(speed + braking * tick, 0);
                    power = brakingPower;
                } else {
                    speed = cruiseSpeed;
                    power = cruisePower;
                }

                pos = train.isUp ? pos + speed * tick : pos - speed * tick;

                train.position = pos;
                train.speed = speed;

                trainData.get(train.id).add(new double[]{t, pos, speed, power});

                if (speed > 0.0 || t == train.startTime)
                    next.add(train);
            }

            active = next;

            if (active.isEmpty()) {
                System.out.println("All trains have stopped at " + t);
                break;
            }
        }

        // Dummy export placeholder
        System.out.println("Simulation finished. Data exported.");
    }

    static class Train {
        int id;
        boolean isUp;
        int startTime;
        double position = 0.0;
        double speed = 0.0;

        Train(int id, boolean isUp, int startTime) {
            this.id = id;
            this.isUp = isUp;
            this.startTime = startTime;
        }

        void init() {
            this.position = isUp ? 0.0 : 9310.0;
            this.speed = 0.0;
        }
    }
}
