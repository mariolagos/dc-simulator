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