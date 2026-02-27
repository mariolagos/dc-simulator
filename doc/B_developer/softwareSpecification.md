# Software Specification
**Document Level:** B (Developer Reference)  
**Version:** 1.3  
**Date:** 2025-11-14
---

## 1. Introduction
The DcSimulator software provides a simulation environment for direct‑current (DC) railway traction systems.  
It models electrical networks with multiple substations, moving trains, and distributed loads using actor‑based concurrency and a numerical solver.  
This specification defines how model requirements from the domain level are implemented in the software architecture.

The document serves as a bridge between domain experts defining model requirements and developers implementing those requirements in code.  
It defines architectural components, data flow, configuration mapping, and traceability links between the model and implementation.

---

## 2  Purpose and Scope

This document defines how the physical, numerical, and architectural requirements of the dc-simulator are implemented.  
It serves as a binding specification between the physical model and the software architecture.  
Scope includes the node-to-node electrical model, solver behaviour, and actor integration.

---

The purpose of this specification is to describe the architecture, numerical framework, and component interactions that implement the physical and functional requirements defined in:

- `modelDescription.md` (system and mathematical model)
- `terms.md` (definitions)
- `USER_GUIDE.md` (usage and scenarios)

This document is maintained as a **living specification**, evolving with the implementation while preserving backward traceability.  
Scope includes:
- Mapping of physical concepts (voltage, current, network) to software entities.
- Description of actor structure and communication.
- Numerical and configuration model for solver execution.
- Traceability between requirements and implementation.

---

## 3. References

| Reference | Title | Purpose |
|------------|--------|----------|
| A1 | `modelDescription.md` | Describes the electrical and mathematical formulation. |
| A2 | `terms.md` | Defines domain and software terminology. |
| A3 | `README_dev.md` | Describes architecture overview and developer workflow. |
| A4 | `testPlan.md` | Describes test strategy and verification coverage. |

---

## 4  System Architecture

### 4.1  Overview
The Symphony configuration represents a **node-to-node resistive DC network** with diode substations and moving train loads.  
Each simulation step computes a quasi-static snapshot of the electrical state by solving Kirchhoff’s Current Law (KCL).  
The network is not discretised at fixed 1 km intervals; node positions follow the physical coordinates of actual electrical connection points.

---

### 4.2  Network Topology and Variables
- Nodes *k = 1 … N* represent electrical connection points (substations, train feeders, junctions).
- Each node carries voltage \( V_k(t) \).
- Branches *i–j* have resistance \( R_{ij} \) and conductance \( G_{ij}=1/R_{ij}\).
- The set of branches defines a sparse nodal admittance matrix Y.

Kirchhoff’s Current Law for node *k*:
\[
\sum_{j\in N(k)} G_{kj}(V_k − V_j)
= I^{sub}_k(V_k) + I^{tr}_k(V_k,t)
\]

Matrix form per time step:
\[
\mathbf Y \mathbf V = \mathbf I_{sub}(\mathbf V) + \mathbf I_{tr}(\mathbf V,t)
\]

---

### 4.3  Substation Model (Thevenin + Ideal Diode)
Each substation *k* is a Thevenin source \( E_k, R_{s,k} \) followed by an ideal diode:

\[
I^{sub}_k(V_k) = \max \!\left( 0, \frac{E_k − V_k}{R_{s,k}} \right)
\]

- If \( V_k < E_k \): forward conduction (current into the line).
- If \( V_k ≥ E_k \): blocked (no backfeed).

**Active-set iteration:**
1. Guess conducting set 𝒞.
2. Stamp \( +1/R_{s,k}\) and \( +E_k/R_{s,k}\) for k∈𝒞.
3. Solve Y·V = I.
4. Update 𝒞 until stable.

---

### 4.4  Train Load Model (Power-Controlled Norton)
Each train at node *k* requests active power \( P_k(t) \) [W]:

\[
I^{tr}_k(V_k,t)
=
\begin{cases}
P^+(t)/\max(V_k,V_{min}), & \text{traction}\\[2mm]
- P^-(t)/\max(V_k,V_{min}), & \text{regeneration}
  \end{cases}
  \]
  where \( P^+=\max(P,0)\), \( P^-=\max(-P,0)\).

Lifecycle gating:
\[
I^{tr}_k=
\begin{cases}
I^{tr}_k, & (t,x)\in \text{active lifecycle}\\
0, & \text{otherwise.}
\end{cases}
\]

Guards: limit traction when \( V_k<V_{op,min}\); clamp regeneration when \( V_k>V_{op,max}\).

---

### 4.5  Time-Stepping and Interpolation
Time step Δt ∈ [0.1 s, 1.0 s].

At each tick:
1. Activate trains by lifecycle.
2. Interpolate \( P_k(t) \) with **endpoint-clamped interpolation**  
 (if t ≤ t₀ → P(t₀); if t ≥ tₙ → P(tₙ)).
3. Assemble RHS currents \( I_{sub}+I_{tr} \).
4. Solve Y·V = I (using sparse solver).
5. Iterate diode set until stable.
6. Compute segment currents \( I_{ij}=G_{ij}(V_i−V_j) \) and losses \( P_{loss}=I_{ij}^2R_{ij}\).

---

### 4.6  Boundary Conditions and Assumptions
- Open ends unless a substation is attached.
- Hard-voltage nodes via Thevenin sources if needed.
- Isolated nodes receive a small leak \( R_{min}\) for stability.
- Resistive only (no L or C).
- Arbitrary node spacing.
- Power profiles are prescribed inputs.
- Quasi-static behaviour (transients neglected).

---

### 4.7  ⚡ Critical Boundaries and Dynamic Multi-Feeding
> _Added 2025-11-14 to formalise the physical and numerical limits of the linear model._

#### A. Supply-Network Boundaries
- **Sectioning points:** introduce transitional resistance when zones are bridged.
- **De-energised section:** V < 600 V → passive or disconnected state with hysteresis.
- **Branching / multi-feed:** rebuild topology when zones connect or isolate.
- **Faults / breakers:** require new Y-matrix.

#### B. Train Operating Limits
- Zero receptivity → resistive brake (\( U>U_{max}\)).
- Partial receptivity → \( P_{regen}=P_{line}+P_{burn}\).
- Low voltage → traction limited (\( U<U_{nom}\)).
- Maintain consistent current/power signs.

#### C. Dynamic Events
Event-driven Y-matrix rebuild for section crossing or switch action.  
Breakers as finite-state machines (open/closed/fault/reset).

#### D. Energy and Stability
Preserve energy at topology changes (using Rₘᵢₙ).  
Loss definition includes I²R and optional transient terms.

#### E. Traffic and Numerics
Map train position to electrical node by interpolation; add Rₘᵢₙ shunts for isolated nodes; Δt < dominant RC time constant.

#### F. Multi-Feeding Types
| Type | Description | Solver Consequence |
|:--|:--|:--|
| Static | Fixed zones fed by separate substations | Single Y-matrix |  
| Dynamic | Zones temporarily connected (train crossing or switch) | Rebuild Y + Rₘᵢₙ damping |

**Critical breakpoints:** Section crossing → new topology; Receptivity = 0 → braking resistor; U < Uₘᵢₙ → traction limit; Dynamic multi-feed → rebuild Y.

---

## 5. Numerical Model Implementation

### 5.1 Core Equation
The system solves the standard nodal admittance equation:

    Y · V = I

where
- *Y* – Admittance matrix assembled from line conductances, substations, and leaks.
- *V* – Node voltages.
- *I* – Current injections from trains and substations.

### 5.2 Iterative Relaxation
Voltages are computed iteratively:

    V_{n+1} = (1 − α)·V_n + α·Y^{-1}·I

where
- α = relaxation factor (`SolverConfig.relaxation`)
- Iteration continues until: |V_{n+1} − V_n| < ε with ε = `SolverConfig.EPS`.

### 5.3 Seed and Initialization
Initial voltages are set from `SolverConfig.seedVoltage`.  
If undefined, a uniform seed (nominal substation voltage) is used.  
Each isolated region ("DcIsland") is seeded separately to ensure convergence.

### 5.4 Numerical Stability
For matrix conditioning, small leak conductances (`G_leak`) are inserted automatically.  
Clamp states at substations enforce unidirectional current flow.

---

## 6. Actor Model and Messaging

### 6.1 Overview
The simulation is implemented with an actor framework (Akka).  
Each actor operates independently and exchanges asynchronous messages.

### 6.2 Actor Interfaces

| Actor | Input messages | Output messages | Description |
|--------|----------------|-----------------|--------------|
| **SimulationController** | `Start`, `Tick`, `Stop` | `Tick` broadcast, `SimulationEnd` | Coordinates global simulation time. |
| **TrainActor** | `Tick`, `TrainStart` | `CurrentInjection` | Computes traction power and current. |
| **GridModelActor** | `Tick`, `CurrentInjection` | `VoltageUpdate` | Runs solver and distributes voltages. |
| **ReporterActor** | `VoltageUpdate`, `Tick` | File output | Records results at each tick. |
| **ConfigLoader/Validator** | `ConfigLoad` | `ConfigOk`, `ConfigError` | Validates presence/range of keys. |
| **TopologyBuilder** | `BuildFromConfig` | `TopologyOk`, `TopologyError` | Maps builder entities to DC network. |

### 6.3 Event Flow Example

    Tick → TrainActor → GridModelActor → ReporterActor → TickEnd

---

## 7. Configuration Mapping

### 7.1 Overview
All configuration parameters are defined in `application.conf` under the `dcsim` root key.  
Parameters are grouped by subsystem.

### 7.2 Example Mapping

| Config Key | Component | Description |
|-------------|------------|--------------|
| `dcsim.solver.relaxation` | `SolverConfig` | Relaxation factor (0–1). |
| `dcsim.solver.eps` | `SolverConfig` | Convergence tolerance. |
| `dcsim.solver.gLeak` | `GridSolver` | Minimum leak conductance. |
| `dcsim.substations` | `Substation` | List of substations with position and voltage. |
| `dcsim.trains` | `TrainActor` | Train templates, profiles, and start times. |
| `dcsim.output.path` | `ReporterActor` | Output directory for CSV/Excel files. |
| `dcsim.validation.requiredKeys` | `ConfigLoader/Validator` | Keys that must exist (R‑07). |
| `dcsim.topology.builder.*` | `TopologyBuilder` | Rules for mapping config entities to nodes/lines (R‑08). |

---

## 8. Error Handling and State Logic

### 8.1 Component States
| Component | States | Transitions |
|------------|---------|-------------|
| SimulationController | Idle → Running → Finished | Controlled by main loop. |
| TrainActor | Inactive → Active → Finished | Triggered by `TrainStart` and `TrainStop`. |
| GridModelActor | Waiting → Solving → Converged | On each `Tick`. |
| ReporterActor | Ready → Logging → Closed | After all ticks complete. |
| ConfigLoader/Validator | Loading → Valid → Error | On startup or reload (R‑07). |
| TopologyBuilder | Building → Ok → Error | During topology generation (R‑08). |

### 8.2 Fault Handling
- Voltage below `Vmin_train` triggers **TrainStop** event.
- Singular matrix detection leads to warning and emergency relaxation.
- Missing/invalid configuration keys produce `ConfigError` with actionable message (R‑07).
- Topology mapping failures produce `TopologyError` with offending entity id (R‑08).
- All fatal errors are escalated to `SimulationController` for graceful shutdown.

---

## 9. Traceability Matrix

| Requirement ID | Description | Implemented in | Verified by |
|----------------|--------------|----------------|--------------|
| R‑01 | Multiple substations with clamps | `Substation`, `GridModelActor` | TestCase A1 |
| R‑02 | Train dynamic injection | `TrainActor`, `SimulationController` | TestCase A3 |
| R‑03 | Numerical relaxation solver | `GridSolver`, `SolverConfig` | UnitTest Solver‑001 |
| R‑04 | Isolated island detection | `GridSolver` | IntegrationTest Island‑002 |
| R‑05 | Voltage logging and report export | `ReporterActor` | RegressionTest RPT‑003 |
| R‑06 | Minimum voltage cutoff | `TrainActor`, `SolverConfig.vmin` | TestCase A5 |
| R‑07 | **Configuration integrity** (required keys, ranges, schema) | `ConfigLoader/Validator` | ConfigSanityCheck, HoconFileTest, ConfigTest |
| R‑08 | **Topology builder correctness** (builder → DC net mapping) | `TopologyBuilder (NetBuilder)` | NetBuilderMiniTests, NetBuilderToDcNetTests, MiniModelBuilder |

---

## 10. Version History

| Date | Version | Notes                                                                                                                                                                                                                      |
|:--|:--------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2025-11-14 | v1.3    | Section 4 fully rewritten to describe node-to-node Symphony model and critical boundaries. Added node-to-node Symphony DC model (§4.1-4.6) and Critical Boundaries (§4.7). All sections retained under MFE + Trace policy. |
| 2025-10-09 | v1.2    | Architecture definition update                                                                                                                                                                                             |
| 2025-09-01 | v1.1    | Initial integration                                                                                                                                                                                                        |

# Deltas

## Delta_v0.8

Algorithm:

1. Order positions: `a = min(posA, posB)`, `b = max(posA, posB)`.
2. For each segment:
  - Compute overlap between `[a, b]` and `[seg.startM, seg.endM]`.
  - If overlap > 0:
    - accumulate `seg.rPerMeter * overlapLength`.
3. Return the accumulated resistance in Ohm.

The utility has no knowledge of nodes or topology.

---

#### 2.4 Solver preparation: adjacency and stamping

During network assembly (before invoking the DC solver):

1. Collect all active nodes where `nodeKind` is `SUBSTATION` or `TRAIN`.

2. Group nodes by `trackId`.

3. For each track:
  - Sort its nodes by `positionM`.

4. For each consecutive node pair `(node_i, node_j)`:
  - Use the PathResolver to compute  
    `R_ij = lineResistance(trackSegments(trackId), node_i.positionM, node_j.positionM)`.
  - Stamp a resistor between the two nodes using the existing solver API  
    (e.g. via a method like `stampResistor(i, j, R_ij)`).

No changes are made to how substations or trains contribute their internal
equivalents (EMF, internal resistance, Norton equivalents, etc.).

The only new responsibility in the solver assembly is building neighbour pairs
along the track and computing their line resistances.

---

#### 2.5 Train node lifecycle

Where the simulator currently manages train activation:

- On creation:
  - set `nodeKind = TRAIN`,
  - assign `trackId`,
  - assign initial `positionM`.

- On each time step:
  - update `positionM` from the train’s current location.

- On removal:
  - deactivate or delete the node using existing mechanisms
  - optionally clear `trackId` and `positionM`.

---

#### 2.6 Substation node initialisation

During configuration loading:

- `nodeKind = SUBSTATION`
- `trackId = <line id>`
- `positionM = <configured position>`

No dynamic behaviour.

---

#### 2.7 Ground node initialisation

The ground/reference node is initialised once:

- `nodeKind = GROUND`
- `trackId = null`
- `positionM = null`

Used only as solver reference; never updated.

---

#### Result

With these minimal deltas:

- the simulator now supports one-node-per-device modelling,
- electrical distance between nodes is derived from R-per-meter track sections,
- GridModelActor builds physically correct adjacency per time step,
- the solver remains untouched and continues to operate on the same matrix API.

This completes the v0.8.M0 specification for the node model and track section
integration.

### v0.8.M0 topology snapshot (ASCII)

          (DC line, modelled as chained R_per_m sections)

pos=0m                pos=5km              pos=12km             pos=20km
|                     |                    |                    |
v                     v                    v                    v

[Sub A]----R----[Train 1]----R----[Sub B]----R----[Train 2]----R----[Sub C]
nodeKind=SUBSTATION        nodeKind=TRAIN          nodeKind=TRAIN
trackId=Line1              trackId=Line1           trackId=Line1
positionM=0                positionM≈5000          positionM≈12000

Where:

- Each device (Sub A, Train 1, Sub B, Train 2, Sub C) has its own electrical node.
- The R between neighbours is computed by integrating R_per_m over the sections
  between their positions:

        R(A, Train1)   = ∫ R_per_m dx over [0 m, 5 km]
        R(Train1, B)   = ∫ R_per_m dx over [5 km, 12 km]
        R(B, Train2)   = ∫ R_per_m dx over [12 km, ...]
        ...

- Only **adjacent nodes along the track** are connected in the solver.

A single ground node provides the reference potential:

             [Sub A]----R----[Train 1]----R----[Sub B]----R----[Train 2]----R----[Sub C]
                |               |              |              |                 |
                +---------------+--------------+--------------+-----------------+
                                              |
                                            [GND]
                                nodeKind=GROUND, no positionM

Key points:

- Trains move by updating `positionM`; topology (who is neighbour to whom) is
  rebuilt each tick before solving.
- The solver API does not change; only the way we assemble the resistive links
  between node indices is updated.

### v0.8.M0 – per-tick sequence (Train movement + DC solve)

Participants:

TrainActor        – one per train, owns kinematic state and power profile
GridModelActor    – owns GridModel, builds topology, calls Solver
GridModel         – holds nodes (incl. nodeKind, trackId, positionM) and track sections
PathResolver      – utility: integrates R_per_m between two positions
Solver            – DC network solver (matrix build + solve)

Tick t:

### v0.8.M0 – Per-tick sequence (ASCII)

Legend:
- TA  = TrainActor
- GMA = GridModelActor
- GM  = GridModel
- PR  = PathResolver
- SOL = Solver

1. Scheduler → TA  
   TA får Tick(t), uppdaterar kinematik och beräknar P_net(t) + positionM(t).

2. TA → GMA : TrainState(t)  
   Innehåller minst (trainId, P_net_W, positionM).  
   GMA uppdaterar motsvarande nod i GM:
  - nodeKind = TRAIN
  - trackId  = <line id>
  - positionM = positionM(t)

3. Scheduler → GMA : SolveTick(t)  
   GMA startar nätverksbyggnad för tid t.

4. GMA → GM : getActiveNodes()  
   Hämtar alla noder med nodeKind = SUBSTATION eller TRAIN.

5. GMA → GM : groupBy(trackId) + sortBy(positionM)  
   För varje trackId får GMA en ordnad lista av noder längs banan.

6. För varje trackId, för varje intilliggande nodpar (i, j):  
   6a. GMA → PR : lineResistance(pos_i, pos_j, trackSections(trackId))  
   PR integrerar R_per_m över sträckan mellan pos_i och pos_j  
   och returnerar R_ij (Ohm).  
   6b. GMA → SOL : stampResistor(node_i, node_j, R_ij)

7. GMA → SOL : stampSources(...)  
   GMA stämplar substationers EMF + interna R, samt tågens
   Norton/Thévenin-ekvivalenter, via befintliga solver-anrop.

8. SOL → GMA : solve()  
   SOL bygger matrisen, löser nätet och returnerar nodspänningar, strömmar osv.

9. GMA → TA : TrainResult(t)  
   För varje tåg skickas t.ex. V_V(t), P_net_W(t) tillbaka.  
   TA uppdaterar sina interna tillstånd och loggar vid behov.


Notes:

- Movement is represented solely by changes in node.positionM for TRAIN nodes.
- GridModelActor rebuilds adjacency from positions on each SolveTick.
- PathResolver is pure: positionA + positionB + segments → R_ij.
- Solver API is unchanged; only the stamping phase uses the new topology logic.

### v0.8.M0 – Debug chain for dynamic node position and line resistance

Goal: Verify that train motion along the track translates into a position-dependent
line resistance and, ultimately, a change in train node voltage.

We break this into explicit checkpoints:

**D1 – Train profile → `pos_m(t)`**

- Source: `TrainActor`
- Signal: `pos_m` in longtable (`Train/<id> pos_m`).
- Expected:
    - `pos_m(t)` is non-decreasing and matches the kinematics profile.
    - When the train has not departed yet, `pos_m(t) ≈ 0`.

**D2 – TrainActor → GridModelActor (`UpdateTrainPower`)**

- Source: `TrainActor` sends `UpdateTrainPower(trainId, ..., positionMeters, speedMS)`.
- Expected:
    - For each tick `t` where the train is active, GMA receives one `UpdateTrainPower`
      with `msg.trainId = trainId` and `msg.positionMeters ≈ pos_m(t)` (up to rounding).
    - No unexpected `null` positions once the train has departed.

**D3 – GMA updates electrical node coordinates**

- Source: `GridModelActor.onUpdateTrainPower`
- Operation:
    - Lookup anchor train node (currently node 99).
    - Compute absolute position:
        - `basePosM(trainId)` = initial node position at profile start.
        - `node.positionM = basePosM(trainId) + floor(msg.positionMeters)`.
- Expected:
    - Debug log shows:
        - `trainNode=99 ... moved from posM=... to posM=... (base=..., delta=...)`.
    - Invariant per tick:
        - `node99.positionM(t) ≈ basePosM + pos_m(t)`.

**D4 – Topology rebuild (`rebuildDynamicLineTopology`)**

- Source: `GridModelActor.onSolveTick`
- Operation:
    - Group all non-GROUND nodes by `trackId`.
    - For each track:
        - Sort nodes by `positionM`.
        - Connect adjacent nodes with `DcLine(fromId, toId, R_ij)`.
    - Store result in `model.setDynamicLineDevices(...)`.
- Expected:
    - Debug log each tick:
        - `[TOPO-DEBUG] train node 99: trackId=..., posM=...`.
        - `[TOPO] track=<id> pair A(posM=...) <-> B(posM=...) R=...`.
        - `[TOPO-TRAIN]` lines where either `A` or `B` is node 99.
    - Invariant:
        - If `node99.positionM` increases away from a substation node,
          then the corresponding `R(track, substation, 99)` increases
          according to `computeLineResistance`.

**D5 – GridModel exposes dynamic lines to the solver**

- Source: `GridModel.getDevices()` / `GridModel.getLines()`.
- Operation:
    - If `dynamicLineDevices` is non-empty:
        - `getDevices()` returns:
            - All non-line devices (substations, trains, etc.) from the static list, plus
            - all dynamic `DcLine` devices.
        - `getLines()` returns only the dynamic lines.
- Expected:
    - No static `Line` devices are used while `dynamicLineDevices` is non-empty.
    - The solver sees exactly the same set of lines as logged by `[TOPO]`.

**D6 – Solver stamping and voltages**

- Source: `DcIterativeSolver` (via `DcIterativeAdapterSolver`).
- Operation:
    - Build `DcNet` from `GridModel` devices (including dynamic `DcLine`s).
    - Stamp each `DcLine` as a resistor `R_ij` between its two nodes.
    - Solve for node voltages.
- Expected (qualitative, 3S1T):
    - For a fixed train power:
        - Moving the train away from the central substation towards the end substations
          increases effective line resistance seen by the train.
        - Train node voltage `Train<V_node_V>` decreases as distance to feeding
          substations increases (all else equal).
    - With an exaggerated `R_per_m` in `computeLineResistance`, the voltage
      drop should be clearly visible in longtable plots.

## v0.8 — Data and topology contracts (additions)

### Absolute position contract
- All positions exchanged between components (config, loaders, actors, solver, logging) are **absolute positions in meters**.
- Relative positions may be derived internally but must not appear at component boundaries.
- This applies to:
    - Track definitions
    - Train run / power profiles
    - Dynamic topology construction
    - Solver inputs and outputs

### Track and run data sources
- The `track` sheet is the **source-of-truth** for track geometry and section ordering.
- The `run` sheet is the **source-of-truth** for train position and power demand.
- Both sheets use a `position` column expressed in **absolute meters**.
- BIS-related columns (`bisKm`, `bisMeter`, `bisPos`) are treated as informational only.

### Dynamic topology construction
- Dynamic line topology is rebuilt per solve tick based on:
    - Node type (substation, train)
    - Track identifier
    - Absolute position ordering
- Line resistance is computed as:
  R_ij = ∫ r_per_meter dx over [min(posA, posB), max(posA, posB)]
- Direction of travel or kilometer numbering does not affect resistance.

### NetBuilder invariants
- NetBuilder must include:
- All substations present in the GridModel
- All TrainLoad devices present in the GridModel
- Substations with identical electrical terminals are rejected.
- Silent skipping of substations or trains is not permitted.

### Solver and logging invariants
- Solver input is exclusively derived from `DcNet`:
- `lines`
- `substations`
- `trains`
- If a train exists in `DcNet.trains()`, the solver must emit:
  Train/<trainId> V_V
  in longtable output.
- No hardcoded node identifiers are allowed in solver or logging logic.

### Scope limitation (explicit)
- v0.8 guarantees correctness for distance and resistance calculations **within a single track section ordering model**.
- Multi-branch routing, shortest-path selection, and forced waypoint routing (e.g. Dijkstra-based pathfinding across junctions) are out of scope for v0.8 and will be addressed in a later version.

## v0.8 — Scope guard: code hygiene and refactoring

The v0.8 milestone explicitly **does not aim to reduce overall code size, class count, or architectural complexity**.
The focus of v0.8 is correctness, determinism, and contract clarity for the stabilized 3S1T baseline.

### No global refactoring rule
- Cross-cutting refactors (e.g. deduplication across packages, large-scale consolidation, architectural cleanup) are **out of scope** for v0.8.
- Known issues such as duplicated functionality, dead code, and legacy structures are acknowledged but deferred to **v0.11**.

### Allowed cleanup (strictly local)
Cleanup is allowed **only** when all of the following conditions are met:
1. The code is already being touched for a v0.8-scoped task (e.g. contract enforcement, topology validation, solver invariant testing).
2. The cleanup is **local** to that code area.
3. Behaviour is protected by:
    - a new or existing minitest, or
    - a regression snapshot (e.g. 3S1T baseline).

Examples of allowed cleanup:
- Removing clearly unused methods or classes within the same package.
- Eliminating trivial duplication discovered while implementing tests.
- Renaming variables or methods to clarify units or contracts.

### Quarantine instead of deletion
- Code that is suspected to be unused but not yet fully proven may be:
    - moved to a `legacy` / `unused` package, or
    - explicitly marked as deprecated with a version tag (e.g. `@Deprecated(since = "v0.8", forRemoval = false)`).
- Permanent removal is deferred until v0.11.

### Backlog policy
All broader cleanup activities shall be tracked as **coarse-grained backlog items**, not incremental refactors:
- Dead code removal sweep
- Deduplication of topology / loader logic
- Consolidation of result and logging infrastructure
- Removal of legacy experimental packages

This rule exists to ensure that v0.8 converges, remains reviewable, and does not regress into exploratory refactoring.

Multiple historical network definition paths exist. v0.8 defines a canonical build pipeline; alternative paths are
supported only for compatibility and will be consolidated in v0.11.

### v0.8 – Testing and logging notes

- v0.8 defines a canonical build path via `GridModel → NetBuilder → DcNet → Solver`.
- Several legacy tests were adapted to comply with stricter contracts (ground node presence, valid device mapping).
- NetBuilder creates `TrainData` only from explicit `TrainLoad` devices; node kind alone is insufficient.
- Longtable logging uses signal `V_node_V` for Train voltage (node voltage at train connection).
  Tests were updated to reflect this current behavior.
- Some assertions were relaxed (e.g. exact train counts, exact voltage equality) to keep v0.8 green.
  Test semantics and logging consistency will be revisited in C0.11.

---

# 📗 softwareSpecification.md — RÅ TEKNISK DELTA

## Deterministic Output Path Resolution (v0.9)

### Objective

Ensure that all simulation output paths are independent of the process working directory and are resolved deterministically.

### Requirements

1. Output directories must not depend on `user.dir`.
2. All relative configuration paths must be resolved relative to the configuration file location.
3. Output roots must be computed centrally.
4. Export writers must receive absolute `Path` objects.
5. Fail-fast behavior must be enforced for invalid path configurations.

### Input Path Resolution

Relative input paths (e.g. `exportRunExcel`, `exportInputs`, templates, legs) shall be resolved against:
<directory of application.conf> ```

Absolute paths shall be used as-is.

Output Path Resolution

All simulation outputs shall be resolved via a central path resolution mechanism.

Writers must not:

Construct relative paths internally.

Use hard-coded directories such as "output" or "results".

Depend on the current working directory.

Validation Requirements

The simulator shall:

Perform strict CSV schema validation.

Throw ValidationInputException for:

Header mismatch.

Missing required columns.

Invalid domain values (e.g. out-of-range position).

Validation must occur before numerical simulation begins.

Java–MATLAB Interface Stability

The long-format result schema shall remain stable:

time_s
project
scenario
base_hash
object_type
object_id
signal
value
unit
stage
iter
note

Changes to this schema require version increment and documentation update.

Non-Functional Requirements

Deterministic behavior across environments.

Reproducible results independent of working directory.

No silent fallback to relative output directories.

Fail-fast behavior for invalid configuration.

Architectural Constraints

Path resolution logic must be centralized.

Export layer must be path-agnostic.

No direct Paths.get("output") or similar literals allowed in simulation entry points.

---

Nu är det ren Markdown, inga artefakter.

Vill du att vi även:

- skriver en kort CHANGELOG-entry?
- eller formulerar en minimal DEV_GUIDE-delta för arkitekturen?