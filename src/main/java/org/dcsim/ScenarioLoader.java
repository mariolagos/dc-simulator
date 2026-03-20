package org.dcsim;

import org.dcsim.math.FieldElement;

import java.io.IOException;
import java.nio.file.Path;

public interface ScenarioLoader<F extends FieldElement<F>> {
    SimulationInputModel<F> load(Path confFile, Path outputRootArg) throws IOException;
}
