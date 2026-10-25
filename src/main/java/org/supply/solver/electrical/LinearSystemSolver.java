package org.supply.solver.electrical;

import org.supply.math.Real;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LinearSystemSolver {

    public Map<String, Real> solveVoltages(AdmittanceSystem system) {
        int n = system.size();
        int ref = system.referenceNodeIndex();
        requireReferenceConnection(system, ref);

        int reducedSize = n - 1;

        double[][] a = new double[reducedSize][reducedSize];
        double[] b = new double[reducedSize];

        int rr = 0;
        for (int row = 0; row < n; row++) {
            if (row == ref) {
                continue;
            }

            int cc = 0;
            for (int col = 0; col < n; col++) {
                if (col == ref) {
                    continue;
                }

                a[rr][cc] = system.conductanceMatrix()[row][col].asDouble();
                cc++;
            }

            b[rr] = system.currentVector()[row].asDouble();
            rr++;
        }

        double[] reducedVoltages = solveDense(a, b);

        Map<String, Real> voltageByNodeId = new LinkedHashMap<>();

        int reducedIndex = 0;
        for (int i = 0; i < n; i++) {
            String nodeId = system.nodeIds().get(i);

            if (i == ref) {
                voltageByNodeId.put(nodeId, Real.fromDouble(0.0));
            } else {
                voltageByNodeId.put(nodeId, Real.fromDouble(reducedVoltages[reducedIndex]));
                reducedIndex++;
            }
        }

        for(var entry:system.nodeIndexById().entrySet())
            voltageByNodeId.put(entry.getKey(),voltageByNodeId.get(system.nodeIds().get(entry.getValue())));
        return voltageByNodeId;
    }


    /** Reject floating components before roundoff can make their matrix look invertible. */
    private static void requireReferenceConnection(AdmittanceSystem system, int reference) {
        int n = system.size();
        boolean[] reached = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        reached[reference] = true;
        queue[tail++] = reference;
        while (head < tail) {
            int row = queue[head++];
            for (int col = 0; col < n; col++) {
                if (reached[col]) continue;
                double forward = system.conductanceMatrix()[row][col].asDouble();
                double reverse = system.conductanceMatrix()[col][row].asDouble();
                if (forward != 0.0 || reverse != 0.0) {
                    reached[col] = true;
                    queue[tail++] = col;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (!reached[i]) {
                throw new IllegalArgumentException("Singular admittance matrix: floating component at node "
                        + system.nodeIds().get(i));
            }
        }
    }

    private static double[] solveDense(double[][] a, double[] b) {
        int n = b.length;

        double[][] m = new double[n][n];
        double[] rhs = new double[n];

        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            rhs[i] = b[i];
        }

        for (int pivot = 0; pivot < n; pivot++) {
            int best = pivot;

            for (int row = pivot + 1; row < n; row++) {
                if (Math.abs(m[row][pivot]) > Math.abs(m[best][pivot])) {
                    best = row;
                }
            }

            if (Math.abs(m[best][pivot]) < 1e-12) {
                throw new IllegalArgumentException(
                        "Singular admittance matrix at pivot " + pivot
                );
            }

            swapRows(m, pivot, best);
            swap(rhs, pivot, best);

            double pivotValue = m[pivot][pivot];

            for (int row = pivot + 1; row < n; row++) {
                double factor = m[row][pivot] / pivotValue;

                if (factor == 0.0) {
                    continue;
                }

                m[row][pivot] = 0.0;

                for (int col = pivot + 1; col < n; col++) {
                    m[row][col] -= factor * m[pivot][col];
                }

                rhs[row] -= factor * rhs[pivot];
            }
        }

        double[] x = new double[n];

        for (int row = n - 1; row >= 0; row--) {
            double sum = rhs[row];

            for (int col = row + 1; col < n; col++) {
                sum -= m[row][col] * x[col];
            }

            x[row] = sum / m[row][row];
        }

        return x;
    }

    private static void swapRows(double[][] matrix, int a, int b) {
        if (a == b) {
            return;
        }

        double[] tmp = matrix[a];
        matrix[a] = matrix[b];
        matrix[b] = tmp;
    }

    private static void swap(double[] values, int a, int b) {
        if (a == b) {
            return;
        }

        double tmp = values[a];
        values[a] = values[b];
        values[b] = tmp;
    }
}