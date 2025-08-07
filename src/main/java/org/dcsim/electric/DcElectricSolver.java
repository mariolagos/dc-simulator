package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.dcsim.math.Real;
import org.dcsim.math.SimMatrixUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DcElectricSolver implements ElectricSolver {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-6;

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

        // Initiala voltages
        RealVector voltageGuess = new ArrayRealVector(n);
        if (!nodeIndexMap.containsKey(model.getGroundNodeId())) {
            throw new IllegalArgumentException("Ground node ID " + model.getGroundNodeId() + " is not present in node list.");
        }
        int groundIndex = nodeIndexMap.get(model.getGroundNodeId());
        voltageGuess.setEntry(groundIndex, 0.0);

        RealVector solution = voltageGuess.copy();
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            yMatrix = SimMatrixUtils.createZeroMatrix(n);
            jVector = SimMatrixUtils.createZeroVector(n);

            // Stampa komponenter
            for (Device<Real> device : model.getDevices()) {
                device.stamp(yMatrix, jVector, solution, timestep, nodeIndexMap);
            }

            // Fixera jordnod
            for (int col = 0; col < n; col++) {
                yMatrix.setEntry(groundIndex, col, 0.0);
            }
            yMatrix.setEntry(groundIndex, groundIndex, 1.0);
            jVector.setEntry(groundIndex, 0.0);

            // Lös
            DecompositionSolver solver = new LUDecomposition(yMatrix).getSolver();
            RealVector nextSolution = solver.solve(jVector);

            double delta = nextSolution.subtract(solution).getNorm();
            solution = nextSolution;
            if (delta < TOLERANCE) break;
        }

        GridResult result = new GridResult(yMatrix, jVector);

        // Spara spänningar
        for (int i = 0; i < n; i++) {
            int nodeId = model.getNodes().get(i).getId();
            Real voltage = Real.fromDouble(solution.getEntry(i));
            result.setVoltage(nodeId, voltage);
        }

        // Uppdatera ström och effekt
        for (Device<Real> device : model.getDevices()) {
            Real current = Real.ZERO;
            Real power = Real.ZERO;

            if (device instanceof Line line) {
                Real fromV = result.getLatestNodeVoltage(line.getFromNode());
                Real toV = result.getLatestNodeVoltage(line.getToNode());
                line.computeCurrent(fromV, toV);
                current = line.getCurrent();
                power = line.getPower(fromV, toV);

            } else if (device instanceof Substation ss) {
                Real fromV = result.getLatestNodeVoltage(ss.getFromNode());
                Real toV = result.getLatestNodeVoltage(ss.getToNode());
                current = ss.computeCurrent(fromV, toV);
                power = ss.computePower(fromV, toV);

            } else if (device instanceof TrainLoad train) {
                Real fromV = result.getLatestNodeVoltage(train.getFromNode());
                Real toV = result.getLatestNodeVoltage(train.getToNode());
                current = train.computeCurrent(fromV, toV);
                power = train.getPower(fromV, toV);
            }

            result.setCurrent(device.getId(), current);
            result.setPower(device.getId(), power);
        }

        // Debug
        System.out.println("=== Y-matrix ===");
        SimMatrixUtils.printMatrix(yMatrix);
        System.out.println("=== J-vector ===");
        SimMatrixUtils.printVectorTransposed(jVector);

        return result;
    }
}
