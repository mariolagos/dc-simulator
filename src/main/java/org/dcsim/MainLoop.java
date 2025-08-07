package org.dcsim;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.electric.*;
import org.dcsim.export.ElectricalExcelExport;
import org.dcsim.math.Real;
import org.dcsim.power.*;

import java.io.File;
import java.util.*;

public class MainLoop {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MainLoop <path/to/application.conf>");
            System.exit(1);
        }

        Config config = ConfigFactory.parseFile(new File(args[0])).resolve();
        GridModel model = GridModelLoader.load(config.getConfig("dcsim").getConfig("grid"));
        DcElectricSolver solver = new DcElectricSolver();

        // Instantiate trains from templates
        List<TrainTemplateInstantiator.TrainInstance> instances = TrainTemplateInstantiator.instantiate(config.getConfig("dcsim"));
        for (TrainTemplateInstantiator.TrainInstance inst : instances) {
            TrainLoad train = new TrainLoad(inst.id(), inst.fromNode(), inst.toNode());
            train.setPowerProfile(inst.profile());
            model.addDevice(train);
            model.setPowerProfile(train.getId(), inst.profile().getPoints());
        }

        // Determine global time range
        double globalStart = instances.stream()
                .flatMap(inst -> inst.profile().getPoints().stream())
                .mapToDouble(PowerPoint::time)
                .min().orElse(0.0);

        double globalEnd = instances.stream()
                .flatMap(inst -> inst.profile().getPoints().stream())
                .mapToDouble(PowerPoint::time)
                .max().orElse(0.0);

        double timestepSeconds = 10.0;

        for (double t = globalStart; t <= globalEnd; t += timestepSeconds) {
            int timestep = (int) Math.round(t / timestepSeconds);

            // 1. Update requested power for all active trains
            for (Device<?> device : model.getDevices()) {
                if (device instanceof TrainLoad train) {
                    Real requestedPower = train.getPowerProfile().getPowerAtTime(t);
                    train.setRequestedPower(requestedPower);

                    PowerPoint ref = train.getPowerProfile().getNearestPoint(t);
                    System.out.printf("t = %.1f s, Train %s: Requested %.1f W at pos %s, speed %.1f m/s%n",
                            t, train.getId(), requestedPower.asDouble(), ref.position(), ref.speed());
                }
            }

            // 2. Solve the electrical network
            GridResult result = solver.solve(model, t, timestep);

            // 3. Store voltage per node
            for (Node node : model.getNodes()) {
                Real voltage = result.getLatestNodeVoltage(node.getId());
                model.appendVoltageResult(node.getId(), t, voltage);
            }

            // 4. Store current/power per device
            for (Device<?> device : model.getDevices()) {
                Real current = result.getLatestDeviceCurrent(device.getId());
                Real power = result.getLatestDevicePower(device.getId());
                model.appendResult(device.getId(), t, current, power);

                if (device instanceof TrainLoad train) {
                    PowerPoint ref = train.getPowerProfile().getNearestPoint(t);
                    PowerPoint updated = new PowerPoint(
                            t,
                            ref.position(),
                            ref.speed(),
                            power.asDouble(),
                            result.getLatestNodeVoltage(train.getToNode()).asDouble(),
                            current.asDouble()
                    );
                    model.appendPowerPoint(train.getId(), updated);
                    train.logCurrents(t, result.getLatestNodeVoltage(train.getFromNode()), result.getLatestNodeVoltage(train.getToNode()));

                    // 5. Kontrollera effektbalans (valfritt)
                    Real uFrom = result.getLatestNodeVoltage(train.getFromNode());
                    Real uTo = result.getLatestNodeVoltage(train.getToNode());
                    Real[] components = train.getPowerBalanceComponents(uFrom, uTo);
                    Real pLine = components[0];
                    Real pBrake = components[1];
                    Real pTotal = pLine.plus(pBrake);

                    System.out.printf(Locale.US,
                            "t = %.1f s, Train %s: U = %.1f V, P_total = %.1f W (line: %.1f, brake: %.1f)%n",
                            t, train.getId(), uFrom.minus(uTo).asDouble(),
                            pTotal.asDouble(), pLine.asDouble(), pBrake.asDouble());
                }
            }
        }

        // Export updated power curves and electrical data
        for (String id : model.getPowerProfileIds()) {
            List<PowerPoint> updatedCurve = model.getUpdatedPowerPoints(id);
            SimpleExcelExport.export(id, updatedCurve, true);
        }

        for (Device<?> device : model.getDevices()) {
            if (device instanceof TrainLoad train) {
                train.exportCurrentsToCsv("output/train_log_" + train.getId() + ".csv");
            }
        }

        ElectricalExcelExport.export("output/electrical_output", model);
        System.out.println("✅ Simulation complete. Results written to output/");
    }
}
