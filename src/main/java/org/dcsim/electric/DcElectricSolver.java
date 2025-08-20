package org.dcsim.electric;

import org.apache.commons.math3.linear.*;
import org.dcsim.math.Real;
import org.dcsim.math.SimMatrixUtils;

import java.util.*;

public class DcElectricSolver implements ElectricSolver {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-9;

    @Override
    public GridResult solve(GridModel model, double time, int timestep) {
        int n = model.getNodes().size();
        RealMatrix yMatrix = SimMatrixUtils.createZeroMatrix(n);
        RealVector jVector = SimMatrixUtils.createZeroVector(n);
        RealVector xVector = SimMatrixUtils.createZeroVector(n);

        Map<Integer, Integer> nodeIndexMap = new HashMap<>();
        List<Node> nodes = model.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            nodeIndexMap.put(nodes.get(i).getId(), i);
        }

        // initial voltages; ground clamped at 0
        if (!nodeIndexMap.containsKey(model.getGroundNodeId())) {
            throw new IllegalArgumentException("Ground node ID " + model.getGroundNodeId() + " is not present in node list.");
        }
        int groundIndex = nodeIndexMap.get(model.getGroundNodeId());
        RealVector solution = new ArrayRealVector(n);
        solution.setEntry(groundIndex, 0.0);

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            yMatrix = SimMatrixUtils.createZeroMatrix(n);
            jVector = SimMatrixUtils.createZeroVector(n);

            // stamp all devices with current operating point
            for (Device<Real> device : model.getDevices()) {
                device.stamp(yMatrix, jVector, solution, timestep, nodeIndexMap);
            }

            // fix ground
            for (int col = 0; col < n; col++) yMatrix.setEntry(groundIndex, col, 0.0);
            yMatrix.setEntry(groundIndex, groundIndex, 1.0);
            jVector.setEntry(groundIndex, 0.0);

            DecompositionSolver solver = new LUDecomposition(yMatrix).getSolver();
            RealVector next = solver.solve(jVector);

            double delta = next.subtract(solution).getNorm();
            solution = next;
            if (delta < TOLERANCE) break;
        }

        GridResult result = new GridResult(yMatrix, jVector);

        // store node voltages
        for (int i = 0; i < n; i++) {
            int nodeId = nodes.get(i).getId();
            result.setVoltage(nodeId, Real.fromDouble(solution.getEntry(i)));
        }

        // update devices currents/powers (+ requested for trains)
        for (Device<Real> device : model.getDevices()) {
            if (device instanceof Line line) {
                Real fromV = result.getLatestNodeVoltage(line.getFromNode());
                Real toV   = result.getLatestNodeVoltage(line.getToNode());
                line.computeCurrent(fromV, toV);
                Real i = line.getCurrent();
                Real p = line.getPower(fromV, toV);
                result.setCurrent(line.getId(), i);
                result.setPower(line.getId(), p);

            } else if (device instanceof Substation ss) {
                Real fromV = result.getLatestNodeVoltage(ss.getFromNode());
                Real toV   = result.getLatestNodeVoltage(ss.getToNode());
                Real i = ss.computeCurrent(fromV, toV);
                Real p = ss.computePower(fromV, toV); // delivered to network
                result.setCurrent(ss.getId(), i);
                result.setPower(ss.getId(), p);

            } else if (device instanceof TrainLoad train) {
                Real fromV = result.getLatestNodeVoltage(train.getFromNode());
                Real toV   = result.getLatestNodeVoltage(train.getToNode());
                Real i = train.computeCurrent(fromV, toV);
                Real p = train.getPower(fromV, toV); // delivered from network to train (motoring +)
                result.setCurrent(train.getId(), i);
                result.setPower(train.getId(), p);
                // requested power (what train wanted this tick)
                result.setRequestedPower(train.getId(), train.getRequestedPower());
                // NOTE: brake resistor power skrivs i writer via train.getBrakeResistorInstantPower(fromV,toV)
            }
        }

        return result;
    }
}
