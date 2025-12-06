# Test Plan
**Document Level:** B (Verification & Validation)  
**Version:** 1.4
**Date:** 2025-10-16

This version merges the legacy taxonomy and A1–A5 with the current Java/JUnit inventory and execution guidance. It supersedes prior drafts while preserving IDs and scope.

---

## 1. Test Taxonomy

| Level | Scope | Typical Target | Notes |
|------|------|-----------------|-------|
| Unit | Function/Class | Utils, stamping helpers | Fast, isolated |
| Component | Module | Solver, GridModel, Train | With fakes/mocks |
| Integration | Multiple modules | Solver + GridModel + IO | End-to-end within process |
| System | Full app | CLI, config, actors | Produces CSV/plots |
| Performance | Throughput/Latency | Solver scaling, I/O | Timed, large configs |
| Regression | Past bugs | Any | Prevents re-introduction |

---

## 2. Canonical Developer Tests (A1–A5)

| Test ID | Level | Description | Target module | Input | Expected result |
|---------|-------|-------------|---------------|-------|-----------------|
| **A1** | Unit | Minimal grid with one node | Solver | `application-test-A1.conf/md` | Stable trivial solution |
| **A2** | Component | Substation + line stamping | Solver, GridModel | `application-test-A2.conf/md` | Correct Y-matrix & solution |
| **A3** | Component | Train load injection | Train, Solver | `application-test-A3.conf/md` | P_req injected into J-vector |
| **A4** | Integration | Multi-train dynamic topology | Controller, GridModel | `application-test-A4.conf/md` | Virtual nodes updated correctly |
| **A5** | System | End-to-end run | All modules | `application-test-A5.conf/md` | CSV outputs as specified |

---

## 3. Extended Test Catalog

### 3.1 Scenario & Integration Tests
| Test ID | Description | Type | Requirement ID(s) | Input | Expected result |
|---------|-------------|------|-------------------|-------|-----------------|
| **SYM-1** | Symphony reference multi-train | System | R-01–R-06 | `application.conf` + profiles | Aggregated voltage/power; timetable alignment |
| **3S-1T** | Three substations, one train | System | R-01–R-05 | case folder | Voltage overlap; load sharing |
| **3S-2T** | Three substations, two trains | System | R-01–R-06 | case folder | Interaction + regen limits |
| **PowerFlowIntegrationIT** | End-to-end power flow | Integration | R-01–R-04 | test harness | Convergent voltages and currents |

### 3.2 Unit Test Inventory (Current)
Populate/confirm using IDE or grep (see Section 5).

| Test Class | Package/Area | Type | Requirement(s) | Notes |
|------------|--------------|------|----------------|-------|
| GridModelActorSplitTest | org.dcsim.actors | Unit | R-03, R-04 | Actor–solver split; topology transitions |
| ConfigSanityCheck | org.dcsim.electric | Unit | R-07 | Config schema/keys sanity |
| ConfigTest | org.dcsim.electric | Unit | R-07 | Config parsing/validation |
| HoconFileTest | org.dcsim.electric | Unit | R-07 | HOCON file handling |
| HoconSanityCheck | org.dcsim.electric | Unit | R-07 | HOCON required fields |
| LineLossNoLoadTest | org.dcsim.electric | Unit | R-03 | Line model / losses at no load |
| AdditionalMiniSolverTests | org.dcsim.unit | Unit | R-03 | Solver edge cases |
| DcIntegrationScenarioTests | org.dcsim.unit | Integration | R-01, R-03 | Multi-component scenario |
| DiodeRegenScenarioTests | org.dcsim.unit | Integration | R-01, R-03 | Regen & clamp behavior |
| DiodeRegenSolverTests | org.dcsim.unit | Unit | R-01, R-03 | Regen numeric limits |
| DiodeSubstationStampTests | org.dcsim.unit | Unit | R-01, R-03 | Substation diode stamping |
| IslandSeparationMiniTests | org.dcsim.unit | Unit | R-04 | Island split/merge mini cases |
| IslandsMiniTests | org.dcsim.unit | Unit | R-04 | Island detection basics |
| IslandsPureTopologyTests | org.dcsim.unit | Unit | R-04 | Topology-only islands |
| MiniMiniSolverTests | org.dcsim.unit | Unit | R-03 | Minimal solver convergence |
| MiniModelBuilder | org.dcsim.unit | Unit | R-08 | Minimal model builder consistency |
| NetBuilderMiniTests | org.dcsim.unit | Unit | R-08 | Net builder rules |
| NetBuilderToDcNetTests | org.dcsim.unit | Unit | R-08 | Builder → DC net mapping |
| OneStationOneTrainTests | org.dcsim.unit | System | R-01, R-02 | Baseline scenario |
| ScenarioSmokeTests | org.dcsim.unit | System | R-01–R-05 | End-to-end smoke |
| SolverMiniTests | org.dcsim.unit | Unit | R-03 | Core solver |
| SolveStaticMiniTests | org.dcsim.unit | Unit | R-03 | Static solve path |
| StampMiniTests | org.dcsim.unit | Unit | R-03 | Stamping rules |
| ThreeSubsProfilesTest | org.dcsim.unit | System | R-01–R-03 | Three substations profiles |
| TrainStampTests | org.dcsim.unit | Unit | R-02, R-03 | Train stamping limits |
| GraphExport | org.dcsim.utils | Unit | R-05 | Export/report utility |
| AnchorStampTest | org.dcsim.testUtils | Unit | R-03 | Anchor stamping |
| OneTrainRunnerMovementTest | org.dcsim.testUtils | Unit | R-02 | Movement/timing |
| SegmentDynTest | org.dcsim.testUtils | Unit | R-02, R-03 | Segment dynamics |

**Notes**
- Fixtures/support classes (ProfilesFixture, NetFixtures, TestSupport) are not counted as tests.
- Proposed requirements for traceability (also added in softwareSpecification v1.1):  
  **R-07** Configuration integrity; **R-08** Topology builder correctness.

---

### 3.3 Tracks Test Matrix
| Tracks | Mandatory Scenario | Requirements | Notes |
|--------|--------------------|--------------|-------|
| 1      | `Tracks1_IsolationAndDropTest` | R‑04, R‑08 | Single-track voltage drop & isolation |
| 2      | `Tracks2_IsolationAndSharingTest` | R‑04, R‑08 | Double-track isolation & load sharing (ev. crossover) |
| 3+     | `Tracks3_ScalingSmokeTest` | R‑04, R‑08 | Convergence + schema only (smoke) |

## 3.4 Detailed Scenario Test Procedures

### 3.4.1 Three Substations + One Train (3S1T) — Regen-Absent Baseline Test

**Purpose**  
Validate correct voltage distribution, substation load sharing, and braking behaviour in a single-train scenario without regenerative braking. Serves as a baseline test for 3S2T.

**Relevant Requirements**  
R-01, R-02, R-03, R-04, R-05.

**Input Configuration**
- Case: `project/3subs1train/scenario1`
- One train (T1), traction + resistive braking only
- Three substations (SS0, SS1, SS2), fixed topology
- Wide-file generated from longtable

**Expected Qualitative Behaviour**
- During motoring:
  - `T1.P_net_W > 0`
  - Train node voltage (`V_V`) lowest near the train
  - Nearest substation supplies most current
- During resistive braking:
  - `T1.P_brk_req_W < 0`
  - `T1.P_brk_net_W ≈ 0`
  - `T1.P_brk_res_W ≈ |P_brk_req_W|`
- No substation exhibits negative `P_net_W`
- Train voltage always within allowed DC band (e.g. 500–900 V)
- Approximate energy balance:  
  Σ P_substations ≈ P_train + P_loss

**Manual Verification Steps**
1. Open the wide-file and inspect:
  - `T1.P_net_W`, `T1.V_V`, `T1.P_brk_req_W`, `T1.P_brk_res_W`.
2. Inspect substations:
  - `SSx.P_net_W`, `SSx.P_loss_W`, `SSx.V_V`.
3. Choose at least one motoring time and one braking time.
  - Verify P = V·I
  - Verify P_loss ≈ (E − V)² / R
4. Confirm consistency across SS0, SS1, SS2.

---

### 3.4.2 Three Substations + Two Trains (3S2T) — Regenerative Interaction Test (Generalized)

**Purpose**  
Validate regenerative braking behaviour, receptivity detection, inter-train energy transfer, and substation absorption in any multi-train configuration where at least one train brakes while another consumes energy.

**Scope**  
This test does not depend on specific timing, power profiles, or timetable data.  
It applies to all scenarios where:
- Train A performs braking,
- Train B performs motoring at overlapping but not necessarily identical times,
- The DC network has at least one substation capable of absorbing or transmitting regenerative energy.

**Relevant Requirements**  
R-01 through R-06.

---

### Expected Behaviour (Independent of Specific Timing)

The simulation shall exhibit **three distinct regen regimes**, regardless of *when* they occur:

#### 1. **No Receptivity Periods**
Conditions:
- Braking train(s) produce regenerated power.
- No other train is motoring sufficiently to receive it.
- Substations cannot accept it (due to operating mode or voltage limits).

Expected signals:
- `P_brk_req_W < 0`
- `P_brk_net_W ≈ 0`
- `P_brk_res_W ≈ P_brk_req_W`
- All substations have `P_net_W ≥ 0` (no backfeed)

---

#### 2. **Partial Receptivity Periods**
Conditions:
- A braking train produces regen.
- One or more trains consume energy, but not enough to absorb all regen.

Expected signals:
- `P_brk_req_W < 0`
- `0 > P_brk_net_W > P_brk_req_W` (partial regen delivered)
- `P_brk_res_W < 0` with reduced magnitude
- Motoring trains have `P_net_W > 0`
- Substations may show reduced net supply, and occasionally slight negative `P_net_W`

---

#### 3. **Full Receptivity Periods**
Conditions:
- One or more motoring trains can absorb all delivered regenerative energy.
- Substations only need to absorb residual energy (if any).

Expected signals:
- `P_brk_res_W ≈ 0`
- `P_brk_net_W ≈ P_brk_req_W`
- Receiving trains show increased `P_net_W > 0`
- One or more substations may have `P_net_W < 0` (absorbing regen surplus)
- Substation losses (`P_loss_W`) remain physically consistent with voltage differences

---

### Global Sanity Conditions (All Scenarios)
These checks must hold for all configurations:

- Train voltages (`V_V`) must track substation voltages with correct IR-drop sign.
- Energy consistency:
  \[
  \sum P_{\text{substations}} + \sum P_{\text{trains}} \approx \sum P_{\text{loss}}
  \]
- No illegal sign transitions (e.g. resistive brake power > braking request).
- No unbounded voltage excursions outside configured system voltage limits.

---

### Quantitative Spot-Check (Scenario-Independent)
For any chosen time during regen activity:

1. For each substation:
  - Read `V_V`, `P_net_W`, `P_loss_W`.
2. Verify:
  - `I = P_net_W / V_V` (consistency with expected load direction)
  - `P_loss ≈ (E − V_V)² / R_int`
3. Deviations within ±5–10% are acceptable for prototype.

---

### Note on Scenario Data
Exact timing of braking, motoring, and receptivity transitions is **scenario-dependent** and shall be documented in the scenario-specific test protocol, not in this test plan.

This generalized procedure applies to *any* future 3S2T scenario using arbitrary driving profiles, timetables, or ramp configurations.

## 4. Test Data and Configuration

| File Type | Purpose | Example |
|----------|--------|---------|
| `.conf` | Simulation layout and parameters | `application.conf`, `templates.conf` |
| `.xlsx` | Train power or timetable profiles | `input/profiles/T1.xlsx` |
| `.csv` | Numerical or aggregated results | `output/voltages.csv`, `output/power.csv` |

All files are UTF-8 and stored under `input/` and `output/` in the repo.

---

## 5. Execution and Automation

### 5.3 Continuous Integration (CI)
Jobs:
- **unit**: `./gradlew test` (eller `mvn -q test`)
- **integration**: `./gradlew integrationTest` (eller `mvn -q -DskipITs=false verify`)
- **regression**: kör case-scenarion + `scripts/compare_csv.sh` mot `baseline/`

Artifacts: CSV-outputs, diffs, loggar.


### 5.1 IntelliJ (JUnit)
- Run → Edit Configurations → + **JUnit**  
  • Test kind: **All in project** or **All in package** (`org.dcsim.unit`, `org.dcsim.it`)  
  • Use classpath of module: select main module
- Pattern runs (examples): `.*Islands.*Test`, `.*SolverMiniTests`

### 5.2 Maven / Gradle
| Tool | Unit | Integration | System run | Regression compare |
|------|------|-------------|------------|--------------------|
| **Maven** | `mvn -q -Dtest='**/*Test' test` | `mvn -q -DskipITs=false verify` | `mvn -q exec:java` | `scripts/compare_csv.sh` |
| **Gradle** | `./gradlew test` | `./gradlew integrationTest` (if present) | `./gradlew run` | `scripts/compare_csv.sh` |

### 5.3 Inventory Commands
- Find in Files (regex): `class .*Test|@Test` scoped to `src/test/java`
- Terminal: `git ls-files 'src/test/java/**' | grep -E 'Test\.java$'`

---

## 6. Acceptance Criteria

| Criterion | Condition | Verified By |
|----------|-----------|-------------|
| Numerical convergence | ΔV < EPS for all nodes | Solver logs |
| Energy conservation | Σ P_sub ≈ Σ P_train ± tolerance | Aggregated results |
| Event sequence validity | Actors follow expected transitions | Logs |
| File output integrity | CSV schema/columns as specified | Validation script |
| Runtime performance | Base case < 5× realtime | CI results |

---

## 7. Reporting and Logging
- Logs under `logs/` and `output/`.
- CI publishes comparison plots (voltage, power) for reference cases.
- Deviations beyond tolerance flagged as warnings.
- Results archived per release.

---

## 8. Version History
| Date | Version | Notes |
|------|---------|-------|
| 2025-10-09 | 1.2 | Merged legacy taxonomy/A1–A5 with Java/JUnit inventory and IDE/Maven/Gradle instructions. |
| 2025-10-09 | 1.1 | Added traceability, case catalog, and data mapping. |
| 2025-09-22 | 1.0 | Initial developer test plan draft (A1–A5). |

- **CI-gate:** Alla **MUST**-märkta tester gröna på default-branch (unit + obligatoriska scenarion).
- **Baseline-diffar:** Inga regressioner utanför dokumenterade toleranser.
