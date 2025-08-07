Iterative DC Solver Implementation Report
Background

To accurately simulate voltage-dependent DC railway systems, an iterative solver was implemented. The solver approximates node voltages using successive updates until convergence is reached.
Solver Design

    Solver Class: DcElectricSolver implements ElectricSolver

    Main Method: GridResult solve(GridModel model, int time)

    Libraries: Apache Commons Math (RealMatrix, RealVector, LUDecomposition)

Iteration Parameters

    MAX_ITERATIONS = 20

    TOLERANCE = 1e-3

Algorithm Steps

    Initialize Y and I: Zeroed conductance matrix Y and current vector I.

    Set Initial Voltage: All node voltages initialized to 1000 V.

    Stamp Devices:

        Iterate over all devices.

        For each TrainLoad, set its power from the PowerPoint curve (if available).

        Call device.stamp(Y, I, V, idx) to update system matrices.

    Solve System: Solve the linear system Y * Vnew = I using LU decomposition.

    Check Convergence: If voltage update < TOLERANCE, break loop.

    Build Result: Return GridResult with voltages, powers, and currents.

Key Features

    All stamping uses the current estimate V.

    Only one GridResult is produced per time step.

    Voltage convergence is measured by maximum delta between iterations.

Outcome

The implementation compiles and runs correctly with:

    Dynamic adjustment of TrainLoad power at each time step.

    Iterative voltage updates for nonlinear convergence.

    Results compatible with export format for Excel post-processing.

Next Steps

    Validate numerical accuracy against test cases.

    Add stamp implementations to all relevant device types.

    Extend to support nonlinear sources (e.g. diode models).

    Integrate multi-train support and movement logic in mainLoop.