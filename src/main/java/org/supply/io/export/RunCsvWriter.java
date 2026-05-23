package org.supply.io.export;

import com.typesafe.config.Config;
import org.supply.domain.RunCsvInput;
import org.supply.loader.RunCsvInputFactory;

import java.nio.file.Path;

import static org.supply.utils.PathUtils.createParent;

public final class RunCsvWriter {

    public void write(Config dcsim, Path confFile, Path exportDir) throws Exception {
        RunCsvInput input = new RunCsvInputFactory().build(dcsim, confFile);
        write(input, exportDir.resolve("run.csv"));
    }

    public void write(RunCsvInput input, Path runCsvFile) throws Exception {
        createParent(runCsvFile);

        RunCsvFromExcel.writeRunCsv(
                input.runExcels(),
                input.trainIds(),
                runCsvFile,
                input.departureTimes(),
                input.exportResolutionS()
        );
    }

}