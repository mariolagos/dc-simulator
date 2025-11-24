# dc-simulator

A modular DC railway power system simulator, written in Java, with a focus on:

- realistic DC substation and catenary modelling,
- train power profiles (traction, coasting, braking),
- traceable numerical behavior (MFE + trace versioning),
- high-resolution time series output for further analysis (CSV “longtable”).

The current baseline scenario is **3S1T v0.1 – static anchor at S2**, which models a single train supplied by three DC substations along a simple feeder.

---

## 1. Scope and goals

`dc-simulator` is a technical playground for studying DC traction networks, not a polished end-user product. The main goals are:

- **Electrical realism**  
  Represent DC feeders, substations and trains with physically meaningful parameters (voltages, internal resistances, line resistances, power limits, etc.).

- **Traceability**  
  Every important change in the physics, numerics or architecture is documented using the **MFE + trace** principles in the `/doc` tree.

- **Inspectability**  
  Simulations produce a detailed **longtable CSV** that can be pivoted and analysed in external tools (Excel, Python, etc.).

- **Experimentation for future extensions**  
  This includes multi-train cases, dynamic topology (train position affecting the admittance matrix), and AC/DC hybrid scenarios.

---

## 2. Current baseline: 3S1T v0.1 – static anchor at S2

The current reference configuration is a **DC feeder with 3 substations and 1 train**:

- 3 DC substations (SS0, SS1, SS2), modelled as Thevenin sources converted to Norton form in the solver.
- 1 DC line with three segments between the substations.
- 1 train, electrically anchored near the middle substation (S2) at a fixed node.
- A predefined train power profile:
   - max traction around `t ≈ 24 s` (≈ 2.4 MW),
   - coasting around `t ≈ 50 s`,
   - max braking around `t ≈ 79 s` (≈ 2.5 MW dissipated in onboard resistors when receptivity is zero).

This baseline has been:

- checked for **KCL consistency** (G·V = J),
- verified for reasonable **line losses** (I²R),
- verified for **substation net power and internal losses**,
- verified for **brake energy split** (requested / to network / to resistor).

### 2.1 Known limitations in v0.1

- The train’s **electrical position is static** (a fixed anchor node near S2).  
  The line is not yet segmented by train position.
- The train load is stamped into **G** (conductance), not J (current injection).  
  As a consequence:
   - `Train1.I_A`
   - `Train1.P_net_W`  
     are **not physically meaningful in v0.1** and should not be used for analysis.
- The “requested power” (`req_W`) is a control-level quantity from the Grid Model Actor (GMA). It may differ slightly from the true catenary power `mot_W`.

### ⚠️ Note on 1-tick offset between mot_W and req_W (3S1T scenario)
In the current 3Subs-1Train (3S1T) implementation, **TrainActor** and **GridModelActor** record power at slightly different stages in the actor pipeline.

- `TrainActor` logs `mot_W(t)` immediately upon receiving `Tick(t)` when reading its power profile.
- `DcSimApp` then triggers `SolveTick(t)` for `GridModelActor` before the train’s `UpdateTrainPower(t)` has been delivered.
- Consequently, `GridModelActor` always uses the previous tick’s power update when solving the network and logs it as `req_W(t)`.

Result:
`req_W(t)` ≈ `mot_W(t − Δt)`

This is a **sequential artifact** of the actor pipeline, not a physical error.  
Power balances remain correct; however, plots (e.g., *longtable*) will show a one-step time shift.  
For 3S1T v0.1 this behaviour is accepted and documented; it will be corrected in v0.2/v0.3 by adding a synchronization barrier between `TrainActor` updates and the solver step.

These limitations are documented and scheduled to be addressed in later versions (see Roadmap).

---

## 3. Repository structure

The most relevant top-level directories are:

```text
doc/
  A_user/
    modelDescription.md      # High-level system and model description
    USER_GUIDE.md            # User-facing guidance (where applicable)
    terms.md                 # Terminology and definitions

  B_developer/
    softwareSpecification.md # How requirements are implemented in code
    README_dev.md            # Developer overview
    README_dev_additions.md  # Additional internal notes
    testPlan.md              # Original test plan
    testPlan_updated.md      # Updated tests and verification
    CONTRIBUTING*.md         # Contribution guidelines

  C_planning/
    prototypePlan.md         # Prototyping phases and status
    Roadmap - Symphony 0.1.0.md  # High-level roadmap
    Backlog.md               # Work items
    docPlan.md               # Documentation planning
    changeRequest*.md        # Explicit change requests

src/
  main/java/...              # Solver, actors, models, etc.
  test/java/...              # Tests (where present)

project/
  ...                        # Example scenarios (config files, longtable outputs, etc.)
```

For most users and reviewers, the entry points are:

- `doc/A_user/modelDescription.md`
- `doc/B_developer/softwareSpecification.md`
- `doc/B_developer/testPlan_updated.md`
- `doc/C_planning/prototypePlan.md`

---

## 4. Building and running

### 4.1 Prerequisites

- Java (JDK 17 or compatible)
- Gradle wrapper included in the repository
- A terminal (Windows CMD is commonly used by the project author)

### 4.2 Typical build

From the repository root:

```bash
# Compile
gradlew clean build

# Run a predefined scenario (example)
gradlew runDcSim -Pargs="--config project/3subs1train/scenario1/application.conf"
```

The exact task names and arguments may evolve over time; refer to:

- `doc/A_user/USER_GUIDE.md` for up-to-date run instructions,
- `doc/B_developer/README_dev.md` for developer-specific run/debug notes.

---

## 5. Output: longtable CSV

Simulations produce a **time series CSV** sometimes called the “longtable”.  
Each row typically contains:

- `time_s` – simulation time in seconds
- `object_type` – e.g. `Train`, `Sub`, `Line`, `Node`
- `object_id` – e.g. `Train1`, `SS1`, `L0`, `S2`
- `signal` – e.g. `mot_W`, `brk_W`, `V_V`, `I_A`, `P_net_W`, `P_loss_W`, `P_brk_res_W`
- `value` – numeric value of the signal
- unit and tags (where applicable)

For **3S1T v0.1**, the following groups of signals are considered stable and validated for analysis:

- **Train (Train1)**
   - `pos_m`, `v_mps`
   - `mot_W` (catenary power at the pantograph)
   - `brk_W` (negative during braking)
   - `aux_W`
   - `req_trac_W`, `req_W` (GMA control signals)
   - `P_brk_req_W`, `P_brk_net_W`, `P_brk_res_W` (brake energy split)

- **Substations (SS0, SS1, SS2)**
   - `V_V` (bus voltage)
   - `I_A` (node current)
   - `P_net_W` (net power into the DC network)
   - `P_loss_W` (internal loss in substation resistance)

- **Lines (L0, L1, L2)**
   - `I_A` (line current)
   - `P_W` (line loss, I²R)

- **Nodes (S1, S2, S3)**
   - `V_V`
   - `I_A`
   - `P_W`

The following signals are **known to be incomplete / not physically correct in v0.1**:

- `Train1.I_A`
- `Train1.P_net_W`

They are scheduled for redefinition once the solver exposes proper per-device power for the train.

---

## 6. Development process and documentation

The project uses a lightweight but strict documentation and traceability structure:

- **A_user/** – user and high-level model descriptions
- **B_developer/** – implementation details, specifications and tests
- **C_planning/** – roadmap, backlog, prototype planning and progress tracking

The **MFE + trace** versioning principle is applied:

- Changes in physics, numerics or architecture are described in the specs,
- Tests are updated in `testPlan_updated.md`,
- Prototypes and milestones are tracked in `prototypePlan.md` and the roadmap.

New code should always be traceable back to:

- a requirement or change request in `doc`, and
- a specific test case or verification step.

---

## 7. Roadmap (high-level)

This is a simplified view; see  
`doc/C_planning/Roadmap - Symphony 0.1.0.md`  
and `doc/C_planning/prototypePlan.md` for details.

### 7.1 Short term (v0.2 – DC baseline refinement)

- Clean up legacy code and old stamping paths.
- Clarify and correct `req_W` semantics vs physical catenary power (`mot_W`).
- Provide physically correct `Train.{I_A, P_net_W}` based on solver device power.
- Align tests and documentation with the 3S1T baseline.

### 7.2 Medium term (v0.3 – dynamic topology, 1 train)

- Introduce line segmentation based on train position.
- Move the train anchor node along the catenary.
- Update the G-matrix incrementally as the train moves.
- Revalidate station loading and line losses as functions of position.

### 7.3 Longer term (3S2T / multi-train)

- Extend the solver and actor model to handle multiple trains.
- Study interactions between trains (voltage drops, shared substations, overlapping braking/traction).
- Add additional test scenarios and baseline longtables.

---

## 8. Contributing

For contribution guidelines, coding style and testing strategy, see:

- `doc/B_developer/CONTRIBUTING.md`
- `doc/B_developer/contributing_primer.md`
- `doc/B_developer/contributing_primer_SmokeAndSanityTests.md`

Bug reports, improvement ideas, and test case suggestions are best driven by:

- explicit references to `softwareSpecification.md` and `testPlan_updated.md`,
- example longtable extracts showing the observed behavior.
