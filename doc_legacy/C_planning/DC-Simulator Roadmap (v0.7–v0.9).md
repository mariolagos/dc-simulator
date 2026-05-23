# dc-simulator

A modular Java-based simulator for DC railway traction networks, focusing on
clear physics, traceable numerical behaviour (MFE + trace), and analysable
high-resolution time-series output.

This repository hosts the current prototype implementation used for studying
DC feeder behaviour under traction and braking, including regenerative power
exchange between trains.

The validated prototype (v0.6) demonstrates correct regenerative braking
behaviour for both 3S1T and 3S2T scenarios and forms the reference point for
future development.

---

## 1. Project overview

`dc-simulator` is designed as a transparent and inspectable simulation
environment for DC traction systems. It combines:

- **Physically meaningful models**  
  (substations as Thevenin sources, resistive DC line, and train power
  components: motoring, braking, auxiliary)

- **Traceability (MFE + trace)**  
  Every change in physics, implementation or numeric behaviour is recorded in
  `/doc`, ensuring that results are reproducible and explainable.

- **High-resolution logging**  
  The simulator outputs a detailed CSV (“longtable”) which can be pivoted into
  wide Excel files for analysis.

- **Modularity**  
  Substations, line segments, trains and solver logic are separated, making the
  system suitable for experimentation and controlled evolution.

This is a technical tool for engineering exploration — not an end-user GUI
application.

---

## 2. Prototype status (v0.6: 3S1T + 3S2T)

The v0.6 prototype has two validated reference scenarios:

### 2.1 3S1T baseline (updated)
A single train supplied by three DC substations.  
The scenario demonstrates:

- correct traction behaviour,
- correct resistive braking (no receptivity),
- proper substation P_net and P_loss behaviour,
- consistent node voltages and power balances,
- stable longtable output matching the wide pivot.

The train is electrically static in v0.6 (no moving anchor).

### 2.2 3S2T regenerative braking (new in v0.6)
Two trains connected to the same DC feeder.

Train1 brakes, Train2 motors with a delay, creating three receptivity regimes:

1. **No receptivity**  
   Train2 inactive → all braking burned in onboard resistors.

2. **Partial receptivity**  
   Train2 motoring with moderate demand → braking power split between Train2 and resistor.

3. **Full receptivity**  
   Train2 motoring strongly → all braking power consumed by Train2, resistor power → 0.

The regenerative behaviour has been verified through:

- longtable power balances,
- wide-file pivot analysis,
- substation power flows,
- train voltages during braking and motoring phases.

A detailed test report (PDF) and Excel evidence files are included (see section 6).

### 2.3 Implementation note: 1-tick offset
`mot_W(t)` and `req_W(t)` are produced at different points in the actor pipeline.
This creates a consistent 1-tick time shift:

`req_W(t)` represents the same physical request as `mot_W(t − Δt)`.

This is a **pipeline characteristic**, not a physics issue.

---

## 3. Architecture overview

The simulator is arranged around the following components:

### 3.1 Solver
A DC network solver computing:

- node voltages,
- device powers (train, substation),
- line currents and losses.

The solver supports:

- multiple substations,
- static DC line segments,
- regenerative power export (negative P_net_W).

### 3.2 Train model (prototype)
Each train consists of:

- **motoring power** (positive),
- **braking power**
    - positive → resistive braking,
    - negative → regenerative braking,
- **auxiliary power** (positive).

The solver uses these components to compute the train’s net electrical power.

### 3.3 Logging: longtable (CSV)
Every timestep outputs:

- object type and id (Train, Sub, Line, Node),
- signal name (P_net_W, brk_W, brk_net_W, V_V, I_A,
  ),
- value,
- optional metadata.

This format allows full post-processing and traceability.

### 3.4 Wide-file Excel
A dedicated tool converts longtable → wide Excel file.

The wide file contains:

- train signals (per column),
- substation signals (per column),
- optional metadata sheets.

Used for 3S1T and 3S2T verification.

---

## 4. Running the simulator

### 4.1 Requirements
- JDK 17+
- Gradle (wrapper included)
- Windows CMD / Linux / macOS terminal

### 4.2 Build

```bash
gradlew clean build
```

### 4.3 Run 3S1T

```bash
gradlew runDcSim -Pargs="--config project/3subs1train/scenario1/application.conf"
```

### 4.4 Run 3S2T

```bash
gradlew runDcSim -Pargs="--config project/3subs2trains/scenario1/application.conf"
```

### 4.5 Generate wide files
After running a scenario, convert the longtable:

```bash
gradlew runLongtableTrainSubWide -Pargs="input.csv output.xlsx"
```

(Exact filenames depend on scenario.)

---

## 5. Documentation hierarchy

The project uses a structured documentation layout:

```
doc/
  A_user/              # Model descriptions and high-level documentation
  B_developer/         # Implementation details, specifications, tests
  C_planning/          # Roadmaps, backlog, change requests
docs/
  test_evidence/       # Test report + wide files for v0.6
  roadmap/             # Roadmap and milestone plans (v0.7+)
```

### Entry points

- **Model description:**  
  `doc/A_user/modelDescription.md`

- **Developer specification:**  
  `doc/B_developer/softwareSpecification.md`

- **Test plans:**  
  `doc/B_developer/testPlan_updated.md`

- **Test evidence (v0.6):**  
  `docs/test_evidence/testrapport_v0.6_prototype.pdf`  
  `docs/test_evidence/test_evidence_v0.6.zip`

- **Roadmap (future versions):**  
  `docs/roadmap/roadmap_v0.7.md`  
  `docs/roadmap/milestone_plan_v0.7-0.9.md`

---

## 6. Test evidence (v0.6)

The v0.6 regenerative prototype is supported by:

### • Test report (PDF)
Demonstrates correct power behaviour in:

- 3S1T (baseline)
- 3S2T (regen interaction)

### • ZIP bundle
Contains:

- 3S1T_wide.xlsx
- 3S2T_wide.xlsx
- associated `application.conf` files

This ZIP and PDF constitute the verifiable reference set for v0.6.

---

## 7. Roadmap (v0.7–v0.9)

Main development phases:

1. **Code cleanup (v0.7)**
2. **Voltage ramping (v0.7)**
3. **Train movement (v0.8)**
4. **Multi-section DC line (v0.8)**
5. **Scenario & project handling (v0.9)**
6. **Output refinements (v0.9)**

Full details:  
`docs/roadmap/roadmap_v0.7.md`  
`docs/roadmap/milestone_plan_v0.7-0.9.md`

---

## 8. Limitations (known in v0.6)

- Trains are electrically static (no movement).
- DC line is unsegmented.
- No voltage-dependent derating (planned in v0.7).
- No mechanical dynamics (requires external CSV profiles).
- No AC-side transformer modelling.
- Pipeline tick offset for motoring/braking signals.

These will be addressed in v0.7–v0.9.

---

## 9. Contributing

Development guidelines and internal notes:

- `doc/B_developer/CONTRIBUTING.md`
- `doc/B_developer/contributing_primer.md`
- `doc/B_developer/contributing_primer_SmokeAndSanityTests.md`

Contributions or test cases should reference the relevant documentation and, if possible, include longtable or wide extracts.

# Deltas

---

## Δ v0.8.M0 – Alignment note for roadmap_v0.7

This note clarifies how the original v0.7 roadmap references to “train movement”
and “multi-section DC line” should be interpreted after the v0.8.M0 design
update.

### From roadmap_v0.7 to v0.8.M0

The v0.7 roadmap states that v0.8 will introduce:

- train movement along the line,
- a multi-section DC line,
- distance-dependent voltage drops.

v0.8.M0 implements these concepts using:

- a **one-node-per-device** electrical topology (substations, trains, ground),
- **track sections with R_per_m** forming a continuous electrical chain,
- dynamic node metadata (`nodeKind`, `trackId`, `positionM`) updated each tick,
- solver-side topology construction:
  - nodes grouped per track,
  - sorted by position,
  - adjacent nodes connected with resistors based on the integrated R_per_m.

Voltage drops therefore follow spatial geometry automatically, without trains
manually computing distances to all substations.

### What remains valid from the original v0.7 roadmap

All high-level intents remain valid:

- v0.7: clean-up, ramping, stable 3S1T/3S2T baseline,
- v0.8: train movement + multi-section DC line,
- v0.9: scenario and project handling + output refinements.

v0.8.M0 should be seen as the **foundational topology step**:

- it provides the electrical node model and line segmentation needed for later
  v0.8 milestones (converter limits, nearest-train-wins, advanced multi-train
  scenarios),
- it does not change the solver API or matrix structure.

Future v0.8.x work will build on this basis to implement converter behaviour,
local absorption limits, and more advanced spatial interactions.


---

## Δ v0.8.M0 – Node Model & Track-Section Interpretation Update

This delta clarifies how v0.8 development should be interpreted in light of the
updated M0 architecture.

### ✔ What v0.8.M0 delivers

- A physically correct **one-node-per-device** electrical topology.
- Each train instance obtains a dynamic electrical node whose `positionM` is
  updated every tick.
- Track segments now carry **R_per_m**, forming a continuous electrical chain.
- The solver no longer relies on a static or heuristic topology:
  - nodes are grouped per track,
  - sorted by position,
  - connected as **adjacent electrical neighbours**,
  - resistances derived from integrating `R_per_m` over intervening sections.
- Voltage drops therefore follow spatial geometry automatically.

**Meaning:**  
“Train movement” no longer implies that trains must calculate distances to every
substation. The solver handles all spatial behaviour via the new node model.

### ✔ How this affects the interpretation of v0.8 items in the roadmap

- “Multi-section DC line” now means a **continuous R_per_m representation**, not
  discrete electrical nodes every 100 m.
- “Dynamic electrical node assignment” refers to **per-device node metadata**
  (`nodeKind`, `trackId`, `positionM`), not topology rewriting.
- “Voltage drop depends on distance” is implemented via **PathResolver +
  adjacency stamping**, not per-train distance queries.
- All solver APIs remain unchanged; only the network assembly logic is enriched.

### ✔ Future work (v0.8.M1 and later)

The following items remain planned exactly as originally stated, but now build on
the M0 topology foundation:

- Converter-based limits (`I_max`, `V_min`, `V_max`)
- Local power absorption and nearest-train-wins behaviour
- Advanced multi-train interactions (3S2T, braking–motoring conflicts)
- Spatial receptivity limits and return-path extensions
- Full rewrite of sS.md to integrate the new topology model

### ✔ Backwards compatibility

- All v0.7 behaviour remains valid.
- Existing solver logic and node index mapping remain unchanged.
- Only metadata and solver-preparation logic have been extended.

---

