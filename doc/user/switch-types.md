# Reusable switchgear input

This extends the existing branch/object preprocessor. The electrical domain model
and solver are unchanged. Explicit inline wiring and legacy six-terminal stations
remain supported.

Define the wiring once in `dcsim.grid.switchgearTypes`:

```hocon
switchgearTypes {
  standard_gap {
    model = DOUBLE_TRACK_GAP
    busbars { PBB1 = FEEDING, PBB2 = FEEDING, PBB3 = FEEDING, PBB4 = FEEDING, NBB = RETURN }
    # connections = [...]  -- full cable list, as in the supplied WE example
  }
}
```

The abbreviated definition above is explanatory only: a valid type must contain
its full `connections` list. The complete example is
`study-examples/WE/WE_grid_branches.conf`.

Each station can now be compact:

```hocon
SS0 = {
  kind = SUBSTATION
  type = standard
  switchgear_type = standard_gap
  positions { N = "101 14+000 N", S = "101 14+000 S" }
}
```

`type` selects existing substation defaults, including rectifier parameters.
`switchgear_type` independently selects the internal wiring. The source parameters
are still required and validated through the existing substation model.
A substation type may itself set `switchgear_type` when all stations using it share
the same wiring. A local selection replaces that inherited wiring choice.
`-SS0` in the branch chain still swaps N/S without changing the wiring's customer
terminal IDs or L/R branch direction.

For a cable data exception on this station:

```hocon
SS0 {
  switchgear.cable_overrides {
    FP1 { length_m = 30, multiplicity = 3 }
    RP1 { conductor_type = cable500 }
  }
}
```

Overrides merge by cable ID. Only `length_m`, `conductor_type` and `multiplicity`
may be changed through this shorthand. Unknown IDs/fields and invalid physical
values fail validation. An override cannot silently rewire endpoints. For a different
electrical topology, define another complete switchgear type or explicit wiring.
A full local `switchgear.connections` list replaces the entire inherited list.
Map fields such as `terminal_ids` merge normally. Place `cable_overrides` on a
station or substation type; the switchgear type contains the canonical cable list.

Station defaults are resolved into explicit objects before object compilation and
report generation. The generated object grid is self-contained; its station objects
no longer need the shorthand to be interpreted. Expanded calculation-grid output
contains no `switchgearTypes` vocabulary.

## Installing and verifying

Unpack the ZIP into the dc-simulator root. Five Java files are changed/new.
The supplied WE configuration is an example copy under `study-examples/WE`, not
a modification of the study on D:.

After testing, copy that example over
`D:/studies/dc-simulator-verifiering/conf/WE/WE_grid_branches.conf` to use the simpler
input. It retains the same six source parameters, five legacy station models,
SS0's reversed orientation, conductor values, branches and reference terminal.
The cable resistance values remain synthetic verification data.

```bat
gradlew.bat test --console=plain
gradlew.bat gridPreprocess --console=plain -Pinput=D:/studies/dc-simulator-verifiering/conf/WE/WE_grid_branches.conf -Poutput=D:/studies/dc-simulator-verifiering/conf/WE/WE_grid_generated.conf -Poverwrite=true
gradlew.bat dcCheck --console=plain -Pargs=D:/studies/dc-simulator-verifiering/conf/WE/WE_run.conf -PworkingDir=D:/studies/dc-simulator-verifiering
```

Use study ID WE_generated in the existing run configuration as previously agreed.
The traffic route definitions already updated to B__/J__ IDs need no further edit.
Connection reports remain next to the generated grid in WE_grid_generated_checks.

Locally: Java 17 compilation and 33 JUnit tests passed (9 new type-resolution tests
plus the previous 24 SwitchGear/report tests). The complete six-station WE
configuration produced an identical expanded electrical grid (83 nodes, 60 external
lines) and byte-identical CSV/text report contents before and after simplification.
The full project's Gradle regression suite must be run in the user's checkout.
Automatic route generation from branch sequences remains the next separate step.
