# modelDescription.md
**Document Level:** A (User / Model Documentation)  
**Purpose:** Describe the physical and numerical model underlying *dcSimulator*.  
**References:** `terms.md`, `USER_GUIDE.md`, `softwareSpecification.md`

---

## 1. Purpose and Scope

The *dcSimulator* models traction power supply networks for DC railways.  
It provides a unified computational framework for trains, substations, lines, and auxiliary components operating on a shared electrical network.

Main goals:
- Evaluate **voltage stability**, **line losses**, and **train power exchange**.
- Support **multi-train** and **multi-substation** operation with regenerative effects.
- Maintain **numerical transparency** so model equations can be traced to physical laws.

Limitations:
- Only **DC steady-state per time-step** (no inductive transients).
- The mechanical model (train speed and force) is handled externally and supplies power demand curves.
- AC feeders and rectifiers are modeled by equivalent DC sources.

---

## 2. System Overview

The electrical network is represented as a **graph**:

    [Substation]--R--(Node1)----R----(Node2)----R----[Train]
         |                               |
       Ground                          Leak/Shunt

Each **node** holds a potential (voltage).  
Each **branch** (line, substation, train) contributes current according to its constitutive law.

At every simulation tick, the network is solved for all node voltages `V`
by enforcing **Kirchhoff’s Current Law** at each node:

    ∑ I_branch = 0

---

## 3. Component Models

### 3.1 Substation

Equivalent circuit:

        EMF (E)
         ─┤├──Rint──► Node
                │
              Diode
                │
              Ground

- **E** = nominal DC voltage (e.g., 750 V, 1500 V, 3000 V).
- **Rint** = internal resistance of the rectifier-transformer group.
- **Diode** = allows current *out* of the substation only (no reverse power).
- Clamp rule:  
  I = (E − Vnode) / Rint,   if Vnode < E  
  I = 0,                    otherwise

### 3.2 Train

Equivalent model: voltage-dependent power sink.

    Node ──► [ Nonlinear current source ] ──► Ground

- Train requests electrical power `P_req` (motoring) or delivers `P_brake` (braking).
- The current drawn is  
  I = P_req / Vnode  
  limited by per-train constraints (`vmin`, `vmax`).
- In braking mode, the sign of `I` is reversed, but limited by network voltage and regenerative acceptance.

### 3.3 Line

Resistive link between nodes *i* and *j*:

    (Node i)──R_line──(Node j)

Resistance can be defined per meter or per section:

    R_line = r_per_m * length

Contributes a symmetric off-diagonal entry to the admittance matrix.

### 3.4 Shunt / Leak

To stabilize the matrix and represent stray return paths,
a small conductance `G_leak` may be added at each node.

    Node ──G_leak──► Ground

This avoids singularities when parts of the network become electrically isolated.

### 3.5 Ground

One node is always designated as **ground** (`groundNodeId` in config).  
Its voltage is fixed to 0 V.

---

## 4. Mathematical Formulation

### 4.1 Nodal Equations

Each node `k` satisfies:

    ∑_j (V_k − V_j)/R_kj + I_k(V_k) = 0

In matrix form:

    Y · V = J

where
- `Y` = nodal admittance matrix (from lines and leaks),
- `V` = node voltage vector,
- `J` = injection current vector (from substations and trains).

### 4.2 Stamping Rules

Each component contributes local entries to `(Y, J)`:

| Component   | ΔY entries                               | ΔJ entries                         |
|--------------|------------------------------------------|------------------------------------|
| Line i–j     | +1/R to Y_ii and Y_jj; −1/R to Y_ij,Y_ji | – |
| Leak G       | +G to Y_ii                               | – |
| Substation   | none (dynamic via current source)         | +I_sub(V) in J_i                   |
| Train        | none (dynamic via current source)         | −I_train(V) in J_i                 |

The solver iterates these currents until convergence.

### 4.3 Clamp Logic

If `Vnode` ≥ `E_substation`, the substation is reverse-biased:

    I_sub = 0

Otherwise, it injects current per ohm’s law.

### 4.4 Island Detection

If a part of the grid becomes disconnected (no substations, only trains),
the solver detects an *island* and halts the iteration for that region.
Voltage is then relaxed toward 0 V or a defined `seed` voltage.

---

## 5. Numerical Strategy

### 5.1 Solver Configuration

`SolverConfig` centralizes numerical constants:
- `EPS` – minimal conductance or tolerance threshold.
- `G_leak` – shunt conductance per node.
- `relaxationFactor` – weight for iterative updates.
- `maxIterations` – solver safety limit.

These parameters ensure stability and repeatability across runs.

### 5.2 Iterative Relaxation

Algorithm sketch:

    repeat until convergence:
        compute component currents
        assemble Y and J
        solve for V
        apply clamps and bounds

Relaxation prevents oscillation when trains and substations interact through strong nonlinearities.

### 5.3 Seeds and Initialization

Each node may have an optional **seed voltage** used at solver start
(e.g., for restarting from previous state or for continuity between ticks).

---

## 6. Simulation Process and Events

### 6.1 Time Loop

At the top level:

    for t in [t_start, t_end] step tickDuration:
        for each Train:
            update position & request power
        GridSolver.solveOnce()
        Reporter.record()

### 6.2 State Machine

    Idle → Running → Paused → Finished

Each event (`Tick`, `TrainStart`, `TrainStop`, `SolveTick`) triggers updates to actor components (`TrainActor`, `GridModelActor`, `ReporterActor`).

### 6.3 Event Types

| Event | Trigger | Effect |
|--------|----------|--------|
| Tick | Simulation clock | Updates all dynamic components |
| TrainStart | Start time reached | Adds new train load |
| TrainStop | End time reached | Removes train |
| SolveTick | After all updates | Performs nodal solve and records |

---

## 7. Validation and Reference Cases

1. **1Sub1Train** – single feed, one moving load; used for voltage–distance validation.
2. **3subs1train** – demonstrates load sharing and voltage overlap.
3. **3subs2train** – tests interaction and regenerative braking.
4. **Symphony** – timetable-based multi-train scenario using real distances and templates.

These cases confirm energy balance, convergence, and proper substation clamping.

---

## 8. Appendix – Symbols and Parameters

| Symbol | Meaning | Typical value |
|---------|----------|---------------|
| E | Substation EMF | 750 V – 3000 V |
| Rint | Internal resistance | 0.05 Ω – 0.2 Ω |
| R_line | Line resistance | 0.05 Ω/km |
| G_leak | Node leak conductance | 1e-5 S |
| EPS | Numerical tolerance | 1e-9 |
| Vmin_train | Train cutoff voltage | 600 V |
| TickDuration | Time step | 0.1–1 s |

---

### References
- *terms.md* – definitions of *stamp*, *island*, *clamp*, *relaxation*, *seed*.
- *softwareSpecification.md* – implementation mapping.
- *USER_GUIDE.md* – operational examples and configuration keys.

---

_This modelDescription.md merges content from legacy files `README2.md`, `TechnicalDescription.md`, `DC_Grid_TechSpec_v1.md`, and `DcSimTechNotes.md` and has been normalized for portability._
