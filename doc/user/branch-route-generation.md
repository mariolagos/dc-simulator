# Generate electrical routes from branches

Add routeDefinitions to the branch input under dcsim.grid:

```hocon
routeDefinitions {
  WE   { branches = ["101", "200", "102"], track = N }
  EW   { branches = ["-102", "-200", "-101"], track = S }
  NwSe { branches = ["201", "200", "202"], track = N }
  SeNw { branches = ["-202", "-200", "-201"], track = S }
}
```

A normal branch reference follows its objects list. A minus-prefixed branch
reference follows that list backwards. The track is explicitly N or S for the
whole route; reversal does not change it. This is independent of -SS0 inside a
branch, which swaps station N/S orientation. Named branches with section/objects
specifications are supported. If a branch name itself starts with a minus, an
exact name match takes precedence over reversal syntax.

GridPreprocessor now emits three kinds of output in the same generation:

- The electrical object grid, as before.
- A separate file containing dcsim.traffic.routes with feeding_lines and return_lines.
- The existing connection CSV/text reports.

When the output grid is WE_grid_generated.conf, the route file is
WE_routes_generated.conf in the same directory. Generally, a stem ending in
_grid_generated becomes _routes_generated; other names gain _routes, for example
network.conf -> network_routes.conf.

Junction connection IDs are found from the generated electrical topology, not
inferred from manually maintained numbering. Junction traversal can use two star
links when neither section is the hub. Existing internal station wiring is used
only to establish electrical adjacency; it does not appear in external route
line lists. Unknown branches/tracks, incorrectly directed/disconnected sequences,
ambiguous junction paths, repeated lines/loops and unknown route fields fail
before outputs are written. In this first version each route traverses full
branches on a single track; partial branches and explicit mid-route track changes
are not supported.

No routeDefinitions preserves the old grid-only preprocessing behavior. It does
not delete a route file left from a previous generation. Route-definition metadata
is removed from the generated grid. The generated route file contains neither
timetable nor templates.

## Traffic include

Remove the handwritten routes block from WE_traffic.conf and include the generated
file at the root instead:

```hocon
include required("WE_routes_generated.conf")

dcsim.traffic {
  # Keep timetable and templates here.
}
```

Do not retain old explicit route lists alongside the generated include.
The package includes a complete WE_traffic.conf example with the existing
four-route timetable/templates preserved and only this include change.

## Installation and WE verification

Unpack the delivery ZIP into the dc-simulator root. Run:

```bat
gradlew.bat test --console=plain
```

Copy both files from study-examples/WE to
D:/studies/dc-simulator-verifiering/conf/WE:
WE_grid_branches.conf and WE_traffic.conf. The study run file must continue to
include WE_grid_generated.conf and WE_traffic.conf. Its study ID remains
WE_generated. No run-file edit or build.gradle.kts edit is included in this patch.

Generate the grid, routes and connection reports:

```bat
gradlew.bat gridPreprocess --console=plain -Pinput=D:/studies/dc-simulator-verifiering/conf/WE/WE_grid_branches.conf -Poutput=D:/studies/dc-simulator-verifiering/conf/WE/WE_grid_generated.conf -Poverwrite=true
```

Expect `Generated routes: ...WE_routes_generated.conf (4 routes)` in the console.
The shared overwrite option covers routes and connection reports as well as the
grid. Ordinary existing-file collisions are checked before writing any output.
Multi-file writes are not an atomic transaction if an I/O failure occurs.

Then check the full study:

```bat
gradlew.bat dcCheck --console=plain -Pargs=D:/studies/dc-simulator-verifiering/conf/WE/WE_run.conf -PworkingDir=D:/studies/dc-simulator-verifiering
```

The two example configurations retain synthetic cable resistance data for WE.
They do not change source parameters or traffic times. Compare with any local
changes made since the previous example before replacing those study files.

## Validation

Locally: Java 17 compilation and 45 JUnit tests passed (12 new generator/CLI tests
plus 33 preceding SwitchGear/type/report tests). Tests cover all four expected WE
routes, reversed branch order, a two-link junction passage, named branches,
invalid directions/references, separate include loading and overwrite protection.
The generated routes plus the traffic include resolved to exactly the previous
full WE traffic configuration; the expanded electrical grid was also identical.
No external RunExcel workbook export or full study timestep solve was performed
here. The full project Gradle regression suite must be run in the user's checkout.
