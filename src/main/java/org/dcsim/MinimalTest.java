package org.dcsim;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.Device;
import org.dcsim.electric.ElectricSolver;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridModelLoader;
import org.dcsim.electric.GridResult;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;
import org.dcsim.power.PowerProfile;
import org.dcsim.power.PowerTemplateParser;

import java.io.File;
import java.util.List;
import java.util.Map;

public class MinimalTest {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: MinimalTest <path-to-conf>");
            System.exit(1);
        }

        File confFile = new File(args[0]);
        if (!confFile.exists()) {
            System.err.println("[ERROR] Config file not found: " + confFile.getAbsolutePath());

            System.exit(2);
        }

        Config config = ConfigFactory.parseFile(confFile).resolve();

        GridModel model = GridModelLoader.load(config.getConfig("dcsim"));

        Map<String, List<PowerPoint>> powerProfiles =
                PowerTemplateParser.parse(config.getConfig("dcsim.powerProfiles"));

        for (Device<Real> device : model.getDevices()) {
            if (device instanceof TrainLoad train) {
                List<PowerPoint> points = powerProfiles.get(train.getId());
                if (points != null) {
                    train.setPowerProfile(new PowerProfile(points));
                }
            }
        }

        ElectricSolver solver = new DcElectricSolver();

        for (int t = 0; t <= 20; t += 5) {
            for (Device<?> device : model.getDevices()) {
                if (device instanceof TrainLoad train) {
                    Real power = train.getPowerProfile().getPowerAtTime(t);
                    train.setRequestedPower(power);
                }
            }

            GridResult result = solver.solve(model, t, t);

            Real u1 = result.getLatestNodeVoltage(1);
            Real u2 = result.getLatestNodeVoltage(2);

            System.out.printf("t = %d s, Node1 = %.1f V, Node2 = %.1f V\n", t, u1.asDouble(), u2.asDouble());

            for (Device<?> d : model.getDevices()) {
                System.out.printf("  %s: I = %.1f A\n", d.getId(), d.getCurrent().asDouble());

                if (d instanceof TrainLoad train) {
                    Real fromU = result.getLatestNodeVoltage(train.getFromNode());
                    Real toU = result.getLatestNodeVoltage(train.getToNode());
                    train.logCurrents(t, fromU, toU);
                }
            }

        }
        for (Device<?> d : model.getDevices()) {
            if (d instanceof TrainLoad train) {
                train.exportCurrentsToCsv("output/" + train.getId() + "_currents.csv");
            }
        }
    }
}
