# Roadmap — Symphony 0.1.0 (Single-Line, A-mode)

**Scope:** One line (single section) DC simulation in **A-mode (open-loop)**. DcSim **reads precomputed train power profiles from disk** (no closed-loop).  
**Goal:** Deterministic run: topology → solver → Excel → tests green → tag `v0.1.0-symphony`.

_Last updated: 2025-10-16_

---

## 1) Milestones

### M1 — 3subs1train (single-line)
**Objective:** One DC line with **3 substations** and **1 train**. Use precomputed power profile from disk.

**Inputs:**
- Topology file: `data/topology/symphony_line.json`
- Train profile: `data/profiles/train_A.csv` (time[s], P[W])
- Substation config: `data/substations/3_subs.json`

**Implementation checklist:**
- [ ] Parse topology and build GridModel (lines, substations, taps if any)
- [ ] Read `train_A.csv` (time, power) into time series
- [ ] For each step _t_: stamp `P(t)` for the train, solve, collect V/I/P
- [ ] Excel export for each step (append rows with time index and signals)
- [ ] Smoke test scenario (short horizon) in CI

**Definition of done (M1):**
- Run completes without errors on dev machine and in CI
- Excel contains substations node voltages and train node current/power for all t
- CI smoke job includes 3subs1train short run

---

### M2 — 3subs2trains (single-line)
**Objective:** One DC line with **3 substations** and **2 trains**. Read **two** power profiles from disk.

**Inputs:**
- Topology file: `data/topology/symphony_line.json` (same as M1)
- Train profiles: `data/profiles/train_A.csv`, `data/profiles/train_B.csv` (time[s], P[W])

**Implementation checklist:**
- [ ] Read and align two profiles on the same time grid (zero-fill or hold-last)
- [ ] Stamp `P_A(t)` and `P_B(t)` each step; solve; collect V/I/P
- [ ] Excel export includes columns for both trains and all substations
- [ ] Deterministic runs: repeated runs produce identical Excel outputs

**Definition of done (M2):**
- Full run completes; Excel validated (columns non-empty, consistent lengths)
- Determinism test: two runs = identical files (hash equal)
- CI full suite runs the short version; smoke still green

---

### M3 — Release 0.1.0 (Symphony)
**Objective:** Tag and publish a reproducible baseline for single-line Symphony.

**Checklist:**
- [ ] Version bumped: `0.1.0`
- [ ] CHANGELOG.md updated
- [ ] `README_dev.md` — "How to run Symphony (A-mode)" section
- [ ] Tag: `v0.1.0-symphony`
- [ ] Release notes: scope + how to run + verified scenarios (M1, M2)

**Definition of done (M3):**
- CI green for smoke + full suite
- Tag reproduces the artifact; instructions in README match reality

---

## 2) A-mode runtime pipeline (single-line)

```
1) Load Topology (JSON)  ─┐
2) Load Substations      ─┤→ Build GridModel → init solver
3) Load Profiles (CSV)   ─┘
4) For t = 0..T-1:
     - P_train_i = profile_i[t] (from disk)
     - Stamp P for each train (Norton: I = -P / max(V, ε))
     - Solve (warm-start with previous solution if available)
     - Record signals (V/I/P) for Excel
5) Write Excel (append rows per t with time, node signals, per-train signals)
```

**Notes:**
- No controller-in-loop (closed-loop) in this release.
- Keep **sign convention**: +P = traction/load, −P = regen/source.
- Use a **stable stamping order** (e.g., substations → lines → trains).

---

## 3) Data formats (proposed)

### 3.1 Topology (`data/topology/symphony_line.json`)
```json
{
  "lineId": 1,
  "nodes": [{"id": 100}, {"id": 200}, {"id": 300}],
  "edges": [
    {"from": 100, "to": 200, "R": 0.01},
    {"from": 200, "to": 300, "R": 0.01}
  ]
}
```

### 3.2 Substations (`data/substations/3_subs.json`)
```json
{
  "substations": [
    {"node": 100, "Vdc": 750.0},
    {"node": 200, "Vdc": 750.0},
    {"node": 300, "Vdc": 750.0}
  ]
}
```

### 3.3 Profiles (CSV)
- `data/profiles/train_A.csv`
- `data/profiles/train_B.csv`

Columns:
```
time_s,P_W
0, 50000
1, 52000
...
```

---

## 4) Excel output (minimum)

Sheet: `Signals` (one row per time step)
```
time_s,
V_node_100,V_node_200,V_node_300,
I_node_100,I_node_200,I_node_300,
P_train_A_W, P_train_B_W
```

(If you prefer per-train sheets, keep the same columns; determinism > layout.)

---

## 5) Tests & CI

**Unit tests:**
- PositionUtils (parsing/formatting) — ✅ already green
- SectionChain round-trip — ✅ already green
- Parser for CSV profiles (read & align)

**Integration tests (short horizon):**
- `M1_Symphony_3subs1train_Smoke`
- `M2_Symphony_3subs2trains_Smoke`

**CI:**
- Smoke job: run M1 fast subset
- Full suite: run M1+M2 short

---

## 6) Run instructions (developer)

**M1 (3subs1train):**
```bash
./gradlew run --args="--topology data/topology/symphony_line.json   --subs data/substations/3_subs.json   --profiles data/profiles/train_A.csv   --excel build/out/symphony_3subs1train.xlsx"
```

**M2 (3subs2trains):**
```bash
./gradlew run --args="--topology data/topology/symphony_line.json   --subs data/substations/3_subs.json   --profiles data/profiles/train_A.csv,data/profiles/train_B.csv   --excel build/out/symphony_3subs2trains.xlsx"
```

---

## 7) Out of scope (for 0.1.0)
- Closed-loop controller (vmin/vmax)
- Multi-line/section runtime chaining
- Actor scheduling / DB telemetry

---
