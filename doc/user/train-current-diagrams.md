# Train current diagrams in DcReporter

Install the two production Java files and the test under the project root. Requires the previously committed TrainCurrentCurve support. No Gradle or solver changes are needed.

DcReporter automatically writes `<study>_train_current_001.svg`, etc. into the results directory. `<study>_train_current_diagrams.txt` lists the generated diagrams. One diagram is produced per recorded traction characteristic; trains sharing a curve share a diagram. Scalar limits use the recorded system voltage ramp. Curve limits use the recorded breakpoint list. A train override therefore groups by its actual limit rather than its template name.

The diagram reads only longtable: static TRAIN i_train_max_A / i_train_limit_curve and SYSTEM u_min_V / u_cutoff_V / u_max_V. Actual operating points pair RESULT u_V with RESULT i_A for exactly the same train and time. Incomplete and nonfinite pairs are excluded. Older results without current characteristics show operating points only; their limits are never inferred from today's configuration. Files with no complete pairs produce no diagrams.

Blue points are traction/zero current, orange points are regeneration. Hovering over a point in an SVG viewer with tooltip support shows train ID, time, voltage and current. SVG retains all complete points; unusually large results can therefore produce large files.

The voltage axis starts at 0.9*u_min and ends at 1.1*u_max, expanding when necessary to retain outlying operating points. The thin black traction envelope stops at u_min and u_max. Actual points outside those bounds remain visible for diagnosis. There is no synthetic lower regenerative envelope: the solver uses RegenerativeTrainElement rather than the negative-current limit in the old image test.

The static curve is evaluated via TrainLoadElement, as in the solver. Different breakpoint representations remain separate groups even when they would happen to interpolate to the same shape. Group numbers are report-local; they are not vehicle variant names. All train IDs are retained in SVG metadata.

Re-run reports on an existing longtable (no new simulation required if the recorded characteristics are present):

```bat
gradlew.bat dcReporter --console=plain -Pargs=D:/studies/cs/dc-calculation/conf/tub_sim_generated.conf -PworkingDir=D:/studies/cs/dc-calculation
```

For older results without static train characteristics, run a new calculation with the current solver to include envelopes.

Tests:

```bat
gradlew.bat test --tests org.supply.app.TrainCurrentDiagramTest --tests org.supply.app.DcReporterTest --console=plain
gradlew.bat test --console=plain
```

The preview SVGs in this package use synthetic longtable test fixtures, not customer data or a physical simulation. This document is README material.
