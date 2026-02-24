package org.dcsim.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

public final class A1TestData {

    private A1TestData() {
    }

    /**
     * Writes a schema-correct but topologically broken network: a line references node 999 (missing).
     */
    public static void writeBrokenNetworkCsvs(Path workdir) throws IOException {
        Files.createDirectories(workdir);

        write(workdir.resolve("nodes.csv"),
                "node_id,track_id,pos_m,kind\n" +
                        "1,1,0.0,BUS\n" +
                        "2,1,1000.0,BUS\n");

        // Broken reference: to_node=999 does not exist in nodes.csv
        write(workdir.resolve("lines.csv"),
                "from_node,to_node,length_m,resistance_ohm\n" +
                        "1,999,1000.0,0.05\n");

        write(workdir.resolve("track.csv"),
                "track_id,start_m,end_m\n" +
                        "1,0.0,1000.0\n");

        // Minimal: one section covering the whole track
        write(workdir.resolve("sections.csv"),
                "section_id,track_id,start_m,end_m\n" +
                        "1,1,0.0,1000.0\n");

        // Minimal: one substation attached to node 1
        write(workdir.resolve("substations.csv"),
                "substation_id,node_id\n" +
                        "1,1\n");

        // Minimal run profile (schema-correct)
        write(workdir.resolve("run.csv"),
                "time_s,train_id,track_id,pos_m,speed_mps\n" +
                        "0,T1,1,0.0,0.0\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8),
                CREATE, TRUNCATE_EXISTING, WRITE);
    }
}
