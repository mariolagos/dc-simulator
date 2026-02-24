package org.dcsim.validation;

import java.nio.file.Path;

public interface MatlabRunner {
    /**
     * Runs MATLAB for the given workdir. Must write results_*.csv in workdir.
     */
    void run(Path workdir) throws Exception;
}
