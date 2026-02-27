package org.dcsim.validation;

import org.dcsim.export.RunCsvWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ValidationTestDataFactory {

    // ---- Scenario alt 1: implement later (v0.9 features) ----
    public void generateNetworkCsvsFromAppConf(Path appConf, Path workdir) throws IOException {
        // TODO: parse appConf -> build GridModel -> write 2..6
        // For now: throw to force scenarios to use CSV_NETWORK until implemented
        throw new UnsupportedOperationException("generateNetworkCsvsFromAppConf not implemented yet");
    }

    // ---- Runs alt 2: generate a minimal valid run.csv ----
    public void generateRunCsv(Path runCsv) throws IOException {
        CsvSchema runSchema = Schemas.RUN_V0_9;
        RunCsvWriter writer = new RunCsvWriter(runSchema);

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of(
                "time_s", "0",
                "train_id", "T1",
                "track_id", "A",
                "position_m", "0",
                "speed_mps", "0"
        ));
        writer.write(runCsv, rows);
    }

    // ---- A1: invalid header variant ----
    public void writeA1_schemaMismatchRunCsv(Path runCsv) throws IOException {
        // wrong headers: t,id...
        Files.writeString(runCsv, "t,id\n0,T1\n", StandardCharsets.UTF_8);
    }

    // ---- A2: out-of-range position ----
    public void writeA2_outOfRangeRunCsv(Path runCsv, double badPosM) throws IOException {
        // Skriv ett schema-korrekt run.csv med en rad där position_m är utanför tillåtet intervall.
        String header = "time_s,train_id,section,track,position_m,P_req_W\n";
        String row = "0.0,T1,S1,TRACK1," + badPosM + ",0.0\n";

        Path parent = runCsv.getParent();
        if (parent != null) Files.createDirectories(parent);

        Files.write(runCsv, (header + row).getBytes(StandardCharsets.UTF_8));
    }
}
