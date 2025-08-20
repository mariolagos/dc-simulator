package org.dcsim.utils;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public final class MatrixPrinter {
    private MatrixPrinter() {}

    public static void printMatrix(String title, RealMatrix M, int maxRows, int maxCols, int precision) {
        System.out.println("=== " + title + " ===");
        int r = Math.min(M.getRowDimension(), maxRows);
        int c = Math.min(M.getColumnDimension(), maxCols);
        String fmt = "% " + (precision + 7) + "." + precision + "f ";

        for (int i = 0; i < r; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < c; j++) {
                sb.append(String.format(fmt, M.getEntry(i, j)));
            }
            if (c < M.getColumnDimension()) sb.append(" ...");
            System.out.println(sb.toString());
        }
        if (r < M.getRowDimension()) System.out.println("...");
    }

    public static void printVector(String title, RealVector v, int max, int precision) {
        System.out.println("=== " + title + " ===");
        int n = Math.min(v.getDimension(), max);
        String fmt = "% " + (precision + 7) + "." + precision + "f ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(String.format(fmt, v.getEntry(i)));
        if (n < v.getDimension()) sb.append(" ...");
        System.out.println(sb.toString());
    }
}
