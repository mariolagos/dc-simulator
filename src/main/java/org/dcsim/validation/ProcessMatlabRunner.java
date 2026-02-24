package org.dcsim.validation;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class ProcessMatlabRunner implements MatlabRunner {

    private final String matlabExecutable;     // e.g. "matlab"
    private final Path matlabScriptOrFunction; // optional, or you embed -r "<cmd>"

    public ProcessMatlabRunner(String matlabExecutable, Path matlabScriptOrFunction) {
        this.matlabExecutable = matlabExecutable;
        this.matlabScriptOrFunction = matlabScriptOrFunction;
    }

    @Override
    public void run(Path workdir) throws Exception {
        // Example command: matlab -batch "run('yourEntry.m');"
        // Better: pass workdir as parameter so MATLAB reads/writes there.
        String cmd = "addpath('" + escape(workdir) + "');"
                + "cd('" + escape(workdir) + "');"
                + "run_validation('" + escape(workdir) + "');"
                + "exit;";

        List<String> args = Arrays.asList(
                matlabExecutable,
                "-batch",
                cmd
        );

        Process p = new ProcessBuilder(args)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .inheritIO()
                .start();

        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("MATLAB failed with exit code " + code);
        }
    }

    private static String escape(Path p) {
        // MATLAB uses single quotes. Escape single quote by doubling.
        return p.toAbsolutePath().toString().replace("'", "''");
    }
}
