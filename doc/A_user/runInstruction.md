# dc-simulator – Run Instructions

This document describes how to run dc-simulator programs

- in IntelliJ IDEA
- from Windows CMD using Gradle

---

# 1. Run DcSimApp (Main application)

## 1.1 IntelliJ IDEA

1. Open class:
   org.dcsim.DcSimApp

2. Right-click → Run 'DcSimApp.main()'

3. Edit Run Configuration:
    - Program arguments:
      project/validationTests/3S1T/application.conf

4. Click Run.

This will:

- Load configuration
- Run export mode (if enabled)
- Or execute solver

---

## 1.2 Windows CMD (Gradle)

List available tasks:

    gradlew.bat tasks --all

If application plugin is configured:

    gradlew.bat run --args="project/validationTests/3S1T/application.conf"

If a custom JavaExec task exists (example):

    gradlew.bat runDcSim --args="project/validationTests/3S1T/application.conf"

If unsure:
Search for task:

    gradlew.bat tasks --all | findstr DcSim

---

# 2. Run Validation Tests

Run all tests:

    gradlew.bat clean test

Run single test class:

    gradlew.bat test --tests org.dcsim.validation.A1SchemaMismatchTest

Run specific test method:

    gradlew.bat test --tests org.dcsim.validation.A1SchemaMismatchTest.A1_runSchemaMismatch_failsFast

---

# 3. Run A1Generator

A1Generator requires arguments:

    A1Generator <A1_cases.csv> <outDir>

## 3.1 IntelliJ

1. Open class:
   org.dcsim.validation.A1Generator

2. Edit Run Configuration
    - Program arguments:
      path/to/A1_cases.csv build/a1output

3. Run.

## 3.2 CMD (Gradle)

If JavaExec task exists:

    gradlew.bat A1Generator.main --args="path/to/A1_cases.csv build/a1output"

Otherwise create a run configuration or execute using java directly.

---

# 4. Export Mode (Materialize Scenario)

If config contains:

    dcsim.exportInputs = ...

or
dcsim.export.enabled = true

Running DcSimApp will:

- Generate CSV inputs
- Write substations.csv
- Write run.csv
- Write longtable.csv (if configured)

No solver execution will occur in export-only mode.

---

# 5. Check Available Tasks

To inspect all Gradle tasks:

    gradlew.bat tasks --all

To filter:

    gradlew.bat tasks --all | findstr run
    gradlew.bat tasks --all | findstr main

---

# 6. Troubleshooting

If you get:

    Cannot locate tasks that match ':DcSimApp.main'

This means:

- There is no Gradle task named DcSimApp.main
- Use 'run' task or configure a JavaExec task

If tests fail:

    gradlew.bat clean test --info

If line-ending diffs appear:

- Ensure .gitattributes defines eol=lf
- Avoid committing .idea directory

---

End of instructions.