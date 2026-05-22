# Prototype Plan (v1.2) — Toward Deliverable System
**Document Level:** C (Planning)  
**Purpose:** Drive DcSimulator from prototype to a **deliverable**, usable for real case studies.  
**Audience:** Product owner, architects, core developers.


---

## 1. Goals (Deliverable DoD)
The system is considered **deliverable (0.9)** when ALL are true:
- Runs from config + data files (no manual hacking).
- Handles **s km+m** chainage for positions and maps onto section table & nodes.
- Supports **double track** topology (two parallel lines, directional running, optional crossovers).
- Imports **precomputed train profiles** from file (CSV/XLSX), applies them per train/timetable.
- Produces stable CSV long-table outputs *(time, object, signal, value)* and summary metrics.
- Minimum case executes end-to-end (chosen real case OR 3subs2train) with acceptance criteria met.
- Documentation A/B updated (USER_GUIDE examples; SoftwareSpec R-07/R-08; TestPlan § 3.1–3.3).

### 1.1 Symphony Baseline Context (added 2025-11-14)
This prototype plan follows the **Symphony node-to-node DC baseline** introduced in version 1.3 of the documentation set.  
Key relationships:

- **A-level** (`A_user/modelDescription.md`) defines the physical and operational model.
- **B-level** (`B_developer/softwareSpecification.md`) formalises the equations, boundaries, and solver design.
- **C-level** (this plan) connects those definitions to milestones, experiments, and acceptance gates.

All prototype workstreams and experiments in this document therefore assume the Symphony network model and its critical-boundary handling.

---

## 2. Scope (Must / Should / Later)
**Must (deliverable 0.9):**
- Fix and pass the **6 blocking unit tests** (stability baseline).
- **s km+m** parsing and mapping to section table & grid nodes.
- **Double track** topology: two lines, per-direction trains, load sharing; at least one crossover supported in config.
- **Profile importer**: read per-train precomputed profile (CSV/XLSX), time-align with tick/timetable.
- Robust CSV outputs; schema locked.

**Should (if time permits):**
- Basic crossover logic selection in config (enable/disable sections).
- Validation scripts for CSV schema and energy balance in CI.

**Later (post-0.9):**
- Performance sweeps, large networks, UI/vis.

---

## 3. Workstreams
| Stream | Goal | Key Requirements | Main Components |
|-------|------|------------------|-----------------|
| **W0 — Stabilize** | 6 unit tests green | R‑03/04/05/07/08 (as relevant) | Solver, Substation, Builder, Config, Reporter |
| **W1 — Chainage (s km+m)** | Parse & map chainage → nodes | R‑08 | ChainageParser, SectionTable, TopologyBuilder |
| **W2 — Double Track** | Two parallel tracks + direction | R‑04/R‑08 | TopologyBuilder, GridModel, Solver |
| **W3 — Profile Import** | Read CSV/XLSX profiles | R‑02/R‑05 | ProfileImporter, Train, Reporter |
| **W4 — Reporting** | Long-table & summaries | R‑05 | ReporterActor, Validators |
| **W5 — Case Assembly** | Minimal real case green | R‑01
R‑06 | Config, Runner, All |

---

## 4. Design & Experiments
| ID | Hypothesis | Setup | Measure | Acceptance |
|----|------------|-------|---------|------------|
| EXP‑C1 | Chainage parser maps “s km+m” to meters deterministically | Sample strings: `S 12+345`, `12+000`, `0+50` | meter value, round-trip | Exact mapping; rejects invalids with clear errors |
| EXP‑C2 | Chainage→SectionTable→Node mapping is monotonic & unique | Section table with known breaks | node index sequence | Strictly increasing; no overlaps; fallbacks handled |
| EXP‑D1 | Double track yields correct isolation & load sharing | Two parallel lines; one/two trains | island count; volt profiles | No unintended coupling; expected drop per track |
| EXP‑P1 | Profile importer aligns time correctly | CSV/XLSX with known timestamps | time deltas vs tick | |Δt| < half‑tick; out-of-range handled |
| EXP‑R1 | Long table & summaries are stable | Baseline run | CSV schema & aggregates | Columns present; totals within tolerance |
| EXP‑S1 | 6 unit tests pass after fixes | Run unit suite | pass/fail | 100% pass for MUST tests |

---

## 5. Milestones & Gates (Critical Path)
| Milestone | Content | Gate / Exit Criteria |
|-----------|---------|----------------------|
| **M0 — Baseline stable** | W0 complete | All 6 unit tests **green**; no flakiness |
| **M1 — Chainage** | W1 complete | EXP‑C1/C2 pass; docs updated (terms + USER_GUIDE §coords) |
| **M2 — Double Track** | W2 complete | EXP‑D1 pass; test case with two tracks runs |
| **M3 — Profile Import** | W3 complete | EXP‑P1 pass; imported profile visible in outputs |
| **M4 — Reporting lock** | W4 complete | EXP‑R1 pass; CSV schema frozen |
| **M5 — Case green (Deliverable 0.9)** | W5 complete | One real case **or** 3subs2train runs end‑to‑end; acceptance in §6 met |

**Parallelization:**
- Run **W0** immediately; in parallel, start **W1 (chainage)**.
- **W2 (double track)** can begin when chainage→nodes exists.
- **W3 (profile)** independent of W2; only needs stable tick/timebase.
- **W4** finalizes once W1–W3 produce consistent outputs.

---

## 6. Acceptance Criteria
*Measured via CI jobs (`unit`, `integration`, `regression`) and stored baselines.* (Deliverable 0.9)
| Criterion | Condition | Verified By |
|----------|-----------|-------------|
| Unit stability | 6 blocking unit tests green | JUnit |
| Chainage mapping | Parses & maps all positions used in case configs | EXP‑C1/C2 + unit tests |
| Double track behavior | Directional trains, expected voltage drops, no ghost coupling | EXP‑D1 + system run |
| Profile ingestion | External profiles aligned within ±½ tick | EXP‑P1 |
| CSV schema | Columns `(time, object, signal, value)` + expected summaries | EXP‑R1 |
| Real case run | One case (or 3subs2train) completes with tolerances | System run + TestPlan §6 |

---

## 7. Risks & Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
| Time pressure (tight window) | Partial features at delivery | Aggressive parallelization W0/W1/W3; hard MUST vs SHOULD |
| Chainage edge-cases | Wrong mapping → bad nodes | Strict parser with unit tests; clear error messages |
| Double track coupling | False islands or leakage | Explicit track IDs; audit stamping; island checks |
| Misaligned profiles | Wrong power vs time | Resample to tick grid; header validation; warn on gaps |
| CSV drift | Broken analysis | Lock schema; CI checkers |

---

## 8. Ownership
| Area | Owner |
|------|------|
| Unit test stabilization (W0) | (assign) |
| Chainage/SectionTable (W1) | (assign) |
| Double track topology (W2) | (assign) |
| Profile importer (W3) | (assign) |
| Reporting & validators (W4) | (assign) |
| Case assembly (W5) | (assign) |

---

## 9. Plan Notes
- This v1.1 replaces the earlier “4-week” cadence with a **compressed, parallel** path aimed at unlocking a **usable system quickly**.
- Documentation updates are part of each milestone (A/B levels).
- After M5 (0.9), we can plan a short hardening pass → 1.0.
- _This plan operates under the Symphony node-to-node DC baseline (see A/B documentation)._

---

## 10. Symbolic Roadmap (Future Option)
*(For planning only — not part of deliverable 0.9)*

### Vision
Enable **symbolic or hybrid (symbolic + numeric)** simulation to support sensitivity analysis, autograd, and parameter studies without rewriting solvers.

### Approach
1. **Scalar abstraction** — introduce a minimal interface `Scalar` with `asDouble()` for numeric fallback.
2. **Algebra context** — encapsulate arithmetic in a small `Algebra` API (today `DoubleAlgebra`; future `SymbolicAlgebra`).
3. **Param wrapping** — represent configuration parameters as lightweight `Param(key, value)` instead of raw doubles.
4. **Stamp isolation** — keep every physical element’s stamping logic in its own functor (`apply(G, J, ctx)`).
5. **Solution container** — return a `Solution` object from solvers (holding voltages + future derivatives/metadata).

### Benefits
- No impact on current code or performance.
- Future-ready for symbolic differentiation and sensitivity studies.
- Decouples solver core from numeric representation.

### Timing
Post-deliverable (after M5).  
Prepare only seams and TODOs in current code (comments like *“// later: Algebra/Scalar support”*).

---


## Version History
| Date | Version | Notes |
|:--|:--|:--|
| 2025-11-14 | v1.2 | Added § 1.1 Symphony Baseline Context; aligned plan notes with current documentation baseline (MFE + Trace). |
| 2025-10-10 | v1.1 | Expanded scope to deliverable chainage/double track/profile import; compressed plan & gates. |
| 2025-10-10 | v1.0 | Initial prototype plan (pre-deliverable). |
