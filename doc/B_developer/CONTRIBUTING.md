# Contributing to DcSim

Thank you for contributing! This guide explains how to keep the `main` branch stable and green.

## Branch and PR Policy
- `main` must always be **green** (CI and `./gradlew test` must pass).
- Create feature branches as `feature/<short-description>`.
- When opening a pull request (PR):
    - `./gradlew test` passes locally.
    - **No new compiler warnings** (`-Xlint` must be clean).
    - Documentation updated when behavior changes (`README_dev.md`, `CHANGELOG.md`).
    - Test names remain **stable**, and all tests use the NetBuilder/Devices path (no reflection).

## Running Tests and Verbosity
- Run all tests:
  ```bash
  ./gradlew test
  ```
- Enable extra logs **opt-in**:
  ```bash
  ./gradlew test -Ddcsim.verbose=true
  ```
  Default runs are silent (no console spam in CI). In tests, keep flags present but make them conditional:
  ```java
  DcDebug.setVerbose(System.getProperty("dcsim.verbose", "false").equals("true"));
  ```

## Quick “Smoke” Test
Run only the most critical test classes for a fast sanity check:
```bash
./gradlew test   --tests org.dcsim.unit.MiniMiniSolverTests   --tests org.dcsim.unit.StampMiniTests   --tests org.dcsim.unit.SolverMiniTests   --tests org.dcsim.unit.IslandSeparationMiniTests
```

## Code Style and Quality
- Keep tests free of reflection (`Class.forName`, `getDeclared*`, `setAccessible`, `Method.invoke`).
- Compiler flags (activated in build):
    - `-Xlint:unchecked,deprecation` for test compilation.
- Generate a coverage snapshot (JaCoCo) locally:
  ```bash
  ./gradlew jacocoTestReport
  ```
  There is no threshold yet — just a baseline.

## Release
- Set the version in `build.gradle.kts`, update `CHANGELOG.md`, and create a git tag `vX.Y.Z`.
- A release must be reproducible from its tag, with all tests passing.

## PR Checklist
- [ ] `./gradlew test` passes (locally + CI).
- [ ] No new warnings.
- [ ] Docs updated if behavior changed.
- [ ] Test names stable; NetBuilder/Devices path used.
- [ ] Verbosity **off** by default (opt-in via `-Ddcsim.verbose=true`).
