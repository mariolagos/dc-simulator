package org.dcsim.solver.impl;

import com.typesafe.config.Config;
import org.dcsim.electric.ElectricSolver;
import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.DcIterativeAdapterSolver; // <-- adapter som implementerar ElectricSolver

public final class SolverFactory {
    private SolverFactory() {}

    public static ElectricSolver create(Config rootConf) {
        String which = "iterative";
        try {
            which = rootConf.getString("dcsim.solver");
        } catch (Exception ignore) {
            // default "iterative"
        }

        switch (which.toLowerCase()) {
            case "iterative":
            case "dciterative":
            case "dc-iterative":
            case "new":
                // Returnera något som implementerar ElectricSolver
                return new DcIterativeAdapterSolver();

            case "legacy":
            case "dcelectric":
            case "old":
                return new DcElectricSolver();

            default:
                return new DcIterativeAdapterSolver();
        }
    }
}
