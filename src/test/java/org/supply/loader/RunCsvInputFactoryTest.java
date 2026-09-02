package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.supply.domain.RunCsvInput;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public final class RunCsvInputFactoryTest {

    @Test
    public void expandsTrainCountUsingHeadway() throws Exception {
        Path tempDir =
                Files.createTempDirectory("run-csv-input-factory-test");

        Path runExcel =
                tempDir.resolve("A-B.xlsx");

        Files.createFile(runExcel);

        Path confFile =
                tempDir.resolve("application.conf");

        Config dcsim =
                ConfigFactory.parseString("""
                        traffic {
                          timetable {
                            trains = [
                              {
                                id = "Train"
                                template_id = "T1"
                                departure = "05:00:00"
                                count = 3
                                headway = "00:01:30"
                                sectionId = "1"
                                trackId = "u"
                                routeId = "U"
                              }
                            ]
                          }

                          templates {
                            T1 {
                              run_excel = "A-B.xlsx"
                            }
                          }
                        }

                        export {
                          exportResolution_s = 1
                        }
                        """);

        RunCsvInput input =
                new RunCsvInputFactory()
                        .build(dcsim, confFile);

        assertEquals(
                java.util.List.of(
                        "Train-001",
                        "Train-002",
                        "Train-003"
                ),
                input.trainIds()
        );

        assertEquals(
                java.util.List.of(
                        18000,
                        18090,
                        18180
                ),
                input.departureTimes()
        );

        assertEquals(3, input.runExcels().size());
        assertEquals(runExcel, input.runExcels().get(0));
        assertEquals(runExcel, input.runExcels().get(1));
        assertEquals(runExcel, input.runExcels().get(2));

        assertEquals(
                java.util.List.of("1", "1", "1"),
                input.sectionIds()
        );

        assertEquals(
                java.util.List.of("u", "u", "u"),
                input.trackIds()
        );
    }

    @Test
    public void keepsOriginalTrainIdWhenCountIsOne() throws Exception {
        Path tempDir =
                Files.createTempDirectory("run-csv-input-factory-test");

        Path runExcel =
                tempDir.resolve("A-B.xlsx");

        Files.createFile(runExcel);

        Path confFile =
                tempDir.resolve("application.conf");

        Config dcsim =
                ConfigFactory.parseString("""
                    traffic {
                      timetable {
                        trains = [
                          {
                            id = "Train"
                            template_id = "T1"
                            departure = "05:00:00"
                            count = 1
                            sectionId = "1"
                            trackId = "u"
                            routeId = "U"
                          }
                        ]
                      }

                      templates {
                        T1 {
                          run_excel = "A-B.xlsx"
                        }
                      }
                    }

                    export {
                      exportResolution_s = 1
                    }
                    """);

        RunCsvInput input =
                new RunCsvInputFactory()
                        .build(dcsim, confFile);

        assertEquals(
                java.util.List.of("Train"),
                input.trainIds()
        );

        assertEquals(
                java.util.List.of(18000),
                input.departureTimes()
        );
    }
}