RealMatrix coefficients = new Array2DRowRealMatrix(new double[][] {
{ 2,  3, -2 },
{ -1, 7,  6 },
{ 4, -3, -5 }
});
RealVector constants = new ArrayRealVector(new double[] { 1, -2, 1 });
DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();
RealVector solution = solver.solve(constants);

projects/
smallCase/
application.conf
loads/
train1.xlsx
train2.xlsx

