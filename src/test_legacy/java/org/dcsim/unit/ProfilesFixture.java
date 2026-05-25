package org.dcsim.unit;

import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny helper to create profile CSV files for scenario tests.
 * Java 17 + JUnit4 friendly (uses TemporaryFolder).
 */
public final class ProfilesFixture {

    private ProfilesFixture() {}

    /** Creates a simple single-train regen profile (T1) and returns the CSV path. */
    public static Path writeRegenSingleTrain(TemporaryFolder tmp) throws IOException {
        Path csv = tmp.newFile("profiles.csv").toPath();
        String body = String.join("\n",
                "time_s,train_id,req_W,iMax_A,vmin_V,vmax_V",
                "0,T1,-100000,300,850,1000",
                "5,T1,-120000,300,850,1000",
                "10,T1,-80000,300,850,1000",
                "15,T1,-100000,300,850,1000"
        );
        Files.write(csv, body.getBytes(StandardCharsets.UTF_8));
        return csv;
    }

    /** Creates a mixed motor+regen two-train profile (T1 motor, T2 regen). */
    public static Path writeMixedMotorRegen(TemporaryFolder tmp) throws IOException {
        Path csv = tmp.newFile("profiles_mixed.csv").toPath();
        String body = String.join("\n",
                "time_s,train_id,req_W,iMax_A,vmin_V,vmax_V",
                // T1 motor (positive req_W), T2 regen (negative req_W)
                "0,T1,  90000,300,850,1000",
                "0,T2,-100000,300,850,1000",
                "5,T1, 100000,300,850,1000",
                "5,T2, -90000,300,850,1000",
                "10,T1, 80000,300,850,1000",
                "10,T2,-110000,300,850,1000"
        );
        Files.write(csv, body.getBytes(StandardCharsets.UTF_8));
        return csv;
    }
}
