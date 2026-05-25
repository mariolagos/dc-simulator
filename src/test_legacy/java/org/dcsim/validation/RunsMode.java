package org.dcsim.validation;

public enum RunsMode {
    EXISTING_RUN_CSV,  // run.csv exists in scenario folder
    GENERATED_RUN_CSV  // run.csv missing -> create in @TempDir
}
