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
    public void buildsConfiguredMeasuredLogSource() throws Exception {
        Path tempDir = Files.createTempDirectory("run-log-input-factory-test");
        Path log = tempDir.resolve("train.csv");
        Files.createFile(log);
        Path confFile = tempDir.resolve("application.conf");

        Config dcsim = ConfigFactory.parseString("""
                simulationControl {
                  simulationStart = "10:00:00"
                  simulationEnd = "11:00:00"
                }
                traffic {
                  timetable.trains = [{
                    id = "T1", template_id = "measured", departure = "10:00:00",
                    count = 1, routeId = "F-M"
                  }]
                  templates.measured {
                    run_log {
                      file = "train.csv"
                      delimiter = ";"
                      columns {
                        time = { name = "Date", format = "yyyy-MM-dd HH:mm:ss.SSS" }
                        speed = { name = "Speed", unit = "m/s" }
                        power = { name = "Power", unit = "kW" }
                      }
                    }
                  }
                }
                """);

        RunCsvInput input = new RunCsvInputFactory().build(dcsim, confFile);

        assertEquals(1, input.runSources().size());
        assertEquals(org.supply.domain.RunSourceType.LOG,
                input.runSources().get(0).type());
        assertEquals(log, input.runSources().get(0).file());
        assertEquals(java.util.List.of(true),
                input.motoringAndAuxiliariesInSameModel());
        assertEquals(java.util.List.of(0.0), input.auxiliaryPowersW());
    }

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
                        simulationControl {
                          simulationStart = "10:00:00"
                          simulationEnd = "11:00:00"
                        }

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

        assertEquals(
                36000,
                input.simulationStartSec()
        );

        assertEquals(
                39600,
                input.simulationEndSec()
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

        assertEquals(
                java.util.Arrays.asList(null, null, null),
                input.relativeLegDepartureTimes()
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
                    simulationControl {
                      simulationStart = "10:00:00"
                      simulationEnd = "11:00:00"
                    }

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

        assertEquals(
                36000,
                input.simulationStartSec()
        );

        assertEquals(
                39600,
                input.simulationEndSec()
        );

        assertEquals(
                java.util.Arrays.asList((Integer) null),
                input.relativeLegDepartureTimes()
        );
    }

    @Test
    public void expandsMultipleLegTemplateForEveryTrain() throws Exception {
        Path tempDir =
                Files.createTempDirectory("run-csv-multiple-legs-test");

        Path firstLeg = tempDir.resolve("A-B.xlsx");
        Path secondLeg = tempDir.resolve("B-C.xlsx");
        Files.createFile(firstLeg);
        Files.createFile(secondLeg);

        Path confFile = tempDir.resolve("application.conf");

        Config dcsim = ConfigFactory.parseString("""
                simulationControl {
                  simulationStart = "06:00:00"
                  simulationEnd = "08:00:00"
                }

                traffic {
                  timetable.trains = [
                    {
                      id = "Train"
                      template_id = "ABC"
                      departure = "06:00:00"
                      count = 2
                      headway = "00:05:00"
                      routeId = "A-C"
                    }
                  ]

                  templates.ABC {
                    motoring_and_auxiliaries_in_same_model = false
                    auxiliary_power_W = 100000
                    legs = [
                      {
                        run_excel = "A-B.xlsx"
                        run_excel_sheet = "+0sek"
                      },
                      {
                        run_excel = "B-C.xlsx"
                        departure = "00:15:00"
                        motoring_and_auxiliaries_in_same_model = true
                        auxiliary_power_W = 200000
                      }
                    ]
                  }
                }
                """);

        RunCsvInput input =
                new RunCsvInputFactory().build(dcsim, confFile);

        assertEquals(
                java.util.List.of(
                        "Train-001", "Train-001",
                        "Train-002", "Train-002"
                ),
                input.trainIds()
        );
        assertEquals(
                java.util.List.of(firstLeg, secondLeg, firstLeg, secondLeg),
                input.runExcels()
        );
        assertEquals(
                java.util.List.of(21600, 21600, 21900, 21900),
                input.departureTimes()
        );
        assertEquals(
                java.util.Arrays.asList(null, 900, null, 900),
                input.relativeLegDepartureTimes()
        );
        assertEquals(
                java.util.List.of("", "", "", ""),
                input.sectionIds()
        );
        assertEquals(
                java.util.List.of("+0sek", "run", "+0sek", "run"),
                input.runExcelSheets()
        );
        assertEquals(
                java.util.List.of(false, true, false, true),
                input.motoringAndAuxiliariesInSameModel()
        );
        assertEquals(
                java.util.List.of(100000.0, 200000.0, 100000.0, 200000.0),
                input.auxiliaryPowersW()
        );
    }
}
