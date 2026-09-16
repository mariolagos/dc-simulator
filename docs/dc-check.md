# Input check and electrical route topology

Run from the dc-simulator project:

```cmd
gradlew dcCheck --console=plain -PconfFile=D:/studies/dc-simulator-verifiering/conf/WE-NWSE/W-E_H5min_+0sek.conf -PworkingDir=D:/studies/dc-simulator-verifiering
```

Print topology for one route, while still validating all routes and traffic:

```cmd
gradlew dcCheck --console=plain -PconfFile=D:/studies/dc-simulator-verifiering/conf/WE-NWSE/W-E_H5min_+0sek.conf -PworkingDir=D:/studies/dc-simulator-verifiering -ProuteId=RED-WE
```

The existing exporter also accepts `--check`:

```cmd
gradlew dcExporter --console=plain -Pargs="--check D:/studies/dc-simulator-verifiering/conf/WE-NWSE/W-E_H5min_+0sek.conf" -PworkingDir=D:/studies/dc-simulator-verifiering
```

## Output

The console report is saved to:

```text
<workingDir>/dc/<studyId>/checks/<studyId>_input_check.txt
```

Normal `exports` and `results` are not modified. Traffic is generated in a temporary
directory and removed after checking. RunExcel generation can take time, but no
electrical timestep is solved. The command fails with a nonzero exit code if
input errors are found. Warnings alone do not fail the command.

## Checks in this first version

- Existing grid, installation, track and system-parameter loader checks.
- Building the actual calculation network, including coordinate transforms and
  six-terminal connections.
- Calculation node/branch IDs, endpoint references, model positions and finite positive branch resistance.
- System reference-node existence and installation feeding/return references and parameters.
- Route line references, empty feeding/return paths and repeated route lines.
- Electrical adjacency between consecutive configured route lines, using shared
  nodes and internal terminal connectors. Other external lines are not used to
  bridge a gap in a route.
- Accidental connections between the route's feeding and return sides.
- Node-ID prefixes required by the current train placer: feeding endpoints must
  start with `F`, return endpoints with `R`.
- RunExcel file/sheet loading and existing leg timing/overlap checks.
- Generated traffic loading, finite exported positions/powers, duplicate train IDs
  at the same timestamp, route references and feeding/return placement of every
  distinct exported route/track/position combination.

## Topology report

Feeding and return lines are listed separately in configured order. Each line
shows its electrical endpoint IDs, model section/track/position and resistance.
Model-coordinate length is shown for endpoints in the same section. Endpoints
are undirected; their display order is not a direction-of-travel declaration.

The report also lists internal terminal connectors and directly attached
diode/thyristor substations and fixed loads. Installation attachment follows
shared nodes/internal connectors, not arbitrary paths through external network lines.

## Limits

`PASS` means that these structural checks passed, not that the electrical solution
will converge or that requested train power is deliverable/receptive.

Existing input loaders stop at their first error. Independent stages continue,
but checks requiring invalid grid/track input are skipped. Fix the reported input
error and rerun to reveal subsequent errors from that loader.

Route checks establish adjacency, not a unique directed traversal or support for
loops. Train placements are checked independently at exported sample locations;
simultaneous train interactions and behavior between samples require a study run.
Static-only cases with no exported train samples produce a warning.

## Tests

```cmd
gradlew test --tests org.supply.app.DcCheckTest --tests org.supply.solver.build.ElectricalRouteCheckTest
```
