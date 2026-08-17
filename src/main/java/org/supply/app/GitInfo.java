package org.supply.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class GitInfo {

    private GitInfo() {
    }

    public static String currentCommitHash() {
        try {
            Process process =
                    new ProcessBuilder(
                            "git",
                            "rev-parse",
                            "HEAD"
                    )
                            .redirectErrorStream(true)
                            .start();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         process.getInputStream()
                                 )
                         )) {

                String hash = reader.readLine();
                int exitCode = process.waitFor();

                if (exitCode != 0
                        || hash == null
                        || hash.isBlank()) {
                    throw new IllegalStateException(
                            "Unable to determine current Git commit hash"
                    );
                }

                return hash.trim();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while determining current Git commit hash",
                    e
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to determine current Git commit hash",
                    e
            );
        }
    }
}
