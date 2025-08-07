package org.dcsim.electric;

public interface ElectricSolver {
    GridResult solve(GridModel model, double time, int timestep);
}
