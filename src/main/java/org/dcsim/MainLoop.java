package org.dcsim;

import java.util.ArrayList;
import java.util.List;

public class MainLoop {
    static final double cruiseSpeed = 22.22;
    static final double accel = 1.0;
    static final double retardation = -1.0;
    static final double cruisePower = 200_000;
    static final double tractionPower = 1_200_000;
    static final double brakingPower = -250_000;
    static final double maxSpeed = 80 / 3.6;

    public static void main(String[] args) throws Exception {
        boolean useSI = true;
        int tick = 1;
        int simEnd = 3600;
        int upDownDelay = 30;

        int upTrainCount = 2;
        int downTrainCount = 2;
        int upStartTime = 0;
        double upTrainStartPosition = 0;
        double upTrainEndPosition = 10_000;

        int downStartTime = 120;
        int upHeadway = 300;
        int downHeadway = 300;
        double downTrainStartPosition = 10_000;
        double downTrainEndPosition = 0;

        // Up trains
        for (int i = 0; i < upTrainCount; i++) {
            List<PowerPoint> train = simulateTrain(i, tick, simEnd, upStartTime + i * upHeadway, true, upTrainStartPosition, upTrainEndPosition);
            SimpleExcelExport.export("Train_" + i + "_up", train, useSI);
        }

        // Down trains
        for (int i = 0; i < downTrainCount; i++) {
            List<PowerPoint> train = simulateTrain(i, tick, simEnd, downStartTime + i * downHeadway, false, downTrainStartPosition, downTrainEndPosition);
            SimpleExcelExport.export("Train_" + i + "_down", train, useSI);
        }
    }

    private static List<PowerPoint> simulateTrain(int trainId, int tick, int simEnd, int startTime, boolean up, double trainStartPosition, double trainEndPosition) {
        List<PowerPoint> points = new ArrayList<>();
        double speed = 0.0;
        double position = trainStartPosition;
        double stopPos = trainEndPosition;
        int time = startTime;

        while (time <= simEnd) {
            double distToStop = Math.abs(stopPos - position);
            double brakeDist = speed * speed / (2 * -retardation);

            double power;
            if (speed < cruiseSpeed && distToStop > brakeDist) {
                speed += accel * tick;
                power = tractionPower;
            } else if (distToStop <= brakeDist && speed > 0) {
                speed = Math.max(0, speed + retardation * tick);
                power = brakingPower;
            } else {
                power = cruisePower;
            }

            position += (up ? 1 : -1) * speed * tick;
            points.add(new PowerPoint(time, formatPosition(position), speed, power, 0, 0));

            if (speed <= 10 && distToStop <= 10) break;
            time += tick;
        }

        return points;
    }

    private static String formatPosition(double pos) {
        int km = (int) pos / 1000;
        int m = (int) pos % 1000;
        return "1 " + km + "+ " + m;
    }
}
