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
- 
# Delta v0.9

## Development Workflow

Development in `dc-simulator` follows an **issue → branch → PR** workflow.

Each change should be tracked by a GitHub issue and implemented in a dedicated feature branch.

### 1. Create or pick an Issue

All development work starts from a GitHub issue.

Examples:

- `#4 Explicit supply/return network model`
- `#5 Refactor simulation pipeline`

### 2. Create a branch

Branch names should reference the issue number.

Format:


issue-<number>-short-description


Examples:


issue-4-explicit-supply-return
issue-5-refactor-pipeline


Create the branch:

```bash
git checkout main
git pull
git checkout -b issue-5-refactor-pipeline
3. Commit changes in small steps

Prefer small, focused commits.

Example commit messages:

Extract SimulationSolver interface (#5)
Move report writing to reporting package (#5)
Refactor DcSimApp composition root (#5)

Rule of thumb:

If a commit message needs two issue numbers, the commit is probably too large.

4. Push the branch
git push -u origin issue-5-refactor-pipeline
5. Open a Pull Request

Create a PR from the branch to main.

PR description example:

Refactor simulation pipeline.

Changes:
- extract scenario loading
- introduce solver interface
- separate reporting from application entry point

Closes #5

If the PR does not fully complete the issue:

Part of #5
6. Merge

When the PR is merged and contains:

Closes #<issue>

GitHub automatically closes the issue.

Project rules

One issue = one branch

Keep commits small and focused

main must always remain buildable

Prefer clear commit messages describing intent

Typical development cycle
Issue → Branch → Commits → Push → PR → Merge → Issue closed
