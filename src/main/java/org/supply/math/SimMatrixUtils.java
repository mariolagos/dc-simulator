package org.supply.math;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;


public class SimMatrixUtils {

    public static RealMatrix createZeroMatrix(int size) {
        return MatrixUtils.createRealMatrix(size, size);
    }

    public static RealVector createZeroVector(int size) {
        return MatrixUtils.createRealVector(new double[size]);
    }

    public static RealVector solve(RealMatrix a, RealVector b) {
        return new LUDecomposition(a).getSolver().solve(b);
    }

    public static RealMatrix removeRow(RealMatrix matrix, int rowToRemove) {
        int n = matrix.getRowDimension();
        RealMatrix result = new Array2DRowRealMatrix(n - 1, n);

        for (int i = 0, ri = 0; i < n; i++) {
            if (i == rowToRemove) continue;
            for (int j = 0; j < n; j++) {
                result.setEntry(ri, j, matrix.getEntry(i, j));
            }
            ri++;
        }

        return result;
    }

    public static void printMatrix(RealMatrix matrix) {
        for (int i = 0; i < matrix.getRowDimension(); i++) {
            for (int j = 0; j < matrix.getColumnDimension(); j++) {
                System.out.printf("%10.4f ", matrix.getEntry(i, j));
            }
            System.out.println();
        }
    }

    public static void printVectorTransposed(RealVector vector) {
        for (int i = 0; i < vector.getDimension(); i++) {
            System.out.printf("%10.4f ", vector.getEntry(i));
        }
        System.out.println();
    }

}
