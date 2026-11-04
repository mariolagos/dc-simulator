# Switchgear connection verification

GridPreprocessor now creates connection reports for stations using explicit
SwitchGear wiring. The reports use the loaded domain wiring also consumed by
CalculationNetworkBuilder. Existing six-terminal stations are not given cable
reports because that model does not specify real internal cable data.

For output `WE_grid_generated.conf`, reports are written beside that file in:

```
WE_grid_generated_checks/
  SS0_connections.csv
  SS0_connections.txt
```

This directory is next to the generated configuration, not under the study's
`dc/<study_id>/checks` directory. Study ID is not used by this standalone tool.

CSV columns include cable ID, customer terminal endpoints, electrical node IDs,
FEEDING/RETURN, endpoint railway positions, physical cable length, conductor type,
resistance per kilometre, multiplicity and computed cable resistance. CSV uses
commas, decimal points, UTF-8 and conventional quoted-field escaping.
Cable length is physical input and is not derived from endpoint coordinates.

The text report adds source characteristics and source node connections, effective
orientation, standard-role to customer-terminal mapping, actual track positions,
external line references and feeding-gap node pairs. NORTH/SOUTH in standard role
names describe the normal terminal layout; actual track is shown separately and
swaps for reversed stations. Gap terminals remain distinct electrical nodes but
can conduct through the declared cables and busbars.

All reports describe static input, not solved currents or voltages. Conductor
resistances in the included SS0 examples are synthetic verification values.

Generate reports with the existing command, from the dc-simulator root:

```bat
gradlew.bat gridPreprocess --console=plain -Pinput=D:/studies/dc-simulator-verifiering/conf/WE/WE_grid_branches.conf -Poutput=D:/studies/dc-simulator-verifiering/conf/WE/WE_grid_generated.conf -Poverwrite=true
```

The same overwrite option applies to both grid and reports. Without overwrite,
existing output files are rejected before writing. Descriptions and reports are
prepared/validated first. A write failure can still leave partial output, so the
operation is not an atomic multi-file transaction. Files for stations removed or
renamed since an earlier run are not deleted; use the current console file count
and current input station list when reviewing old report directories.

Tests:

```bat
gradlew.bat test --tests org.supply.app.SwitchGearConnectionReportTest --console=plain
gradlew.bat test --console=plain
```

Locally: Java 17 compilation and 24 JUnit tests passed (6 new report/CLI tests plus
18 existing SwitchGear tests). Generation was also checked with the six-station
WE input: SS0 produces the CSV/text pair; the five legacy stations do not.
The full repository regression suite must be run in the actual project.

This change adds reports only. Simplified reusable switchgear type input and
branch-based route generation remain subsequent preprocessing work.
