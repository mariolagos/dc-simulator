package org.supply.solver.electrical;

import org.supply.math.Real;

public final class MatrixPrinter {

    private MatrixPrinter() {
    }

    public static void printSystem(
            String title,
            AdmittanceSystem system,
            int maxRows,
            int maxCols,
            int precision
    ) {
        System.out.println("=== " + title + " ===");
        printMatrix("G", system.conductanceMatrix(), maxRows, maxCols, precision);
        printVector("J", system.currentVector(), maxRows, precision);
    }

    public static void printMatrix(
            String title,
            Real[][] matrix,
            int maxRows,
            int maxCols,
            int precision
    ) {
        System.out.println("=== " + title + " ===");

        int rows = Math.min(matrix.length, maxRows);
        int cols = matrix.length == 0
                ? 0
                : Math.min(matrix[0].length, maxCols);

        String format = "% " + (precision + 7) + "." + precision + "f ";

        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();

            for (int col = 0; col < cols; col++) {
                line.append(String.format(
                        format,
                        matrix[row][col].asDouble()
                ));
            }

            if (cols < matrix[row].length) {
                line.append(" ...");
            }

            System.out.println(line);
        }

        if (rows < matrix.length) {
            System.out.println("...");
        }
    }

    public static void printVector(
            String title,
            Real[] vector,
            int maxRows,
            int precision
    ) {
        System.out.println("=== " + title + " ===");

        int rows = Math.min(vector.length, maxRows);
        String format = "% " + (precision + 7) + "." + precision + "f ";

        for (int row = 0; row < rows; row++) {
            System.out.println(String.format(
                    format,
                    vector[row].asDouble()
            ));
        }

        if (rows < vector.length) {
            System.out.println("...");
        }
    }
}