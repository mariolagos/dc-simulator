## Two-stage DC calculation and incremental global reporting

### Purpose

Each study is calculated independently and stores its results in its own study-specific results directory. Reporting is invoked explicitly and does not require rerunning the DC solver. Global installation statistics accumulate across studies in a shared workbook.

### Stage 1: Calculate a study

Run the usual study workflow with the study's configuration. For example, `WE` and `two_sections` produce independent results under `dc/WE/results/` and `dc/two_sections/results/`. A different RunExcel sheet can be studied as a separate study, e.g. `WE_mrt` using `minRunTime` instead of `+0sek`; one study selects one calculation case.

### Stage 2: Process results on demand

To regenerate the per-study installation report from existing solver results, without invoking the solver:

```cmd
gradlew dcReporter --console=plain -Pargs=D:/studies/dc-simulator-verifiering/conf/WE/WE_run.conf
```

To add or replace the WE installation contribution in the shared global workbook:

```cmd
gradlew dcGlobalInstallation --console=plain -Pinput=D:/studies/dc-simulator-verifiering/dc/WE/results/WE_results_installation.xlsx -Poutput=D:/studies/dc-simulator-verifiering/dc/global/substation_global.xlsx -PstudyId=WE -PcalcType=+0sek
```

To add another study, use its installation-results input and the **same** global output path:

```cmd
gradlew dcGlobalInstallation --console=plain -Pinput=D:/studies/dc-simulator-verifiering/dc/two_sections/results/two_sections_results_installation.xlsx -Poutput=D:/studies/dc-simulator-verifiering/dc/global/substation_global.xlsx -PstudyId=two_sections -PcalcType=DC
```

`DC` above is only the provisional calculation-type label used in the manual `two_sections` test; use the actual case label when known. At present, `StudyId` and `Calc Type` are explicit command arguments, even if the study configuration contains the corresponding information.

### Incremental update contract

- The global workbook is created if absent and updated if present.
- Contributions are keyed by (`StudyId`, `Calc Type`); reprocessing the same key **replaces**, rather than duplicates, its installation rows.
- Rows belonging to other study/case keys are retained. Do not sum unrelated studies together.
- `Sources` records provenance by case. `Metadata` describes the most recently processed source, not all sources.
- Close the destination workbook in Excel before updating it (Windows file locks).
- Use a fresh test output path for verification; do not overwrite an established global workbook while experimenting.
- Incremental reporting is currently implemented for **Global Installation**. Global Train still requires a design for combining representative legs from multiple trains and should not be described as incrementally supported yet.

### Verified manual regression procedure

1. Start with a new global test workbook. Process `WE` (`+0sek`): expect 6 installation rows.
2. Process `two_sections` against the same output: expect 8 rows (6 + 2).
3. Process `WE` again: expect 8 rows, without duplicates; verify the `two_sections` rows are unchanged.
4. Check `Sources`, `Metadata`, and `Method` sheets. Run the JUnit4 regression test:

```cmd
gradlew test --tests org.supply.report.SubstationGlobalIncrementalTest --console=plain
```

### Design boundary

Simulation produces study-specific physical results. Explicit result processing generates per-study reports and updates shared global reports. Calculation and reporting are independently invocable; global accumulation spans studies, while each study/case remains identifiable. An analysis interval may be shorter than the simulation interval. Future Global Train work must handle partial train runs and representative journeys assembled from comparable legs, with full provenance.
