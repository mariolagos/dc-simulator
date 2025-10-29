# Software Specification
**Document Level:** B (Requirements → Architecture)  
**Version:** 1.1  
**Date:** 2025‑10‑09

---

## 1. Introduction
The DcSimulator software provides a simulation environment for direct‑current (DC) railway traction systems.  
It models electrical networks with multiple substations, moving trains, and distributed loads using actor‑based concurrency and a numerical solver.  
This specification defines how model requirements from the domain level are implemented in the software architecture.

The document serves as a bridge between domain experts defining model requirements and developers implementing those requirements in code.  
It defines architectural components, data flow, configuration mapping, and traceability links between the model and implementation.

---

## 2. Purpose and Scope
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

## 4. System Architecture

### 4.1 Overview
The DcSimulator consists of a **simulation kernel** and a set of **actors** communicating asynchronously.  
Actors represent independent components: trains, substations, the electrical grid, and reporting mechanisms.

    +-----------------------+        +--------------------+
    |  SimulationController |  -->   |  TrainActors (n)   |
    +-----------------------+        +--------------------+
                 ↓                              ↓
          +------------------+        +--------------------+
          |  GridModelActor  | <----> |   ReporterActor   |
          +------------------+        +--------------------+

### 4.2 Major Components

| Component | Responsibility | Notes |
|------------|----------------|--------|
| **SimulationController** | Central time and event coordinator. Spawns actors and synchronizes ticks. | Receives global config. |
| **TrainActor** | Represents an individual train as a dynamic electrical load. | Reads timetable and power profile. |
| **Substation** | Provides DC voltage with internal resistance and clamp diode. | Injects current into network. |
| **GridModelActor** | Maintains nodal admittance matrix and executes numerical solver. | Uses `GridSolver`. |
| **GridSolver** | Performs iterative relaxation to solve voltages. | Configured by `SolverConfig`. |
| **ReporterActor** | Collects simulation data and writes results to file. | CSV/Excel/telemetry output. |
| **ConfigLoader/Validator** | Loads and validates configuration from HOCON/JSON. | Ensures schema integrity (R‑07). |
| **TopologyBuilder (NetBuilder)** | Builds DC network topology from config/fixtures. | Ensures mapping correctness (R‑08). |

### 4.3 Data Flow
Each simulation tick triggers a sequence:
1. **Controller** broadcasts tick event.
2. **Trains** compute instantaneous power and send current injections.
3. **GridModel** assembles the system matrix and solves voltages.
4. **Reporter** records voltages, currents, and power per node.
5. **Controller** advances to next tick.

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

| Date | Version | Notes |
|------|----------|-------|
| 2025‑10‑09 | v1.1 | Added R‑07 (Configuration integrity) and R‑08 (Topology builder correctness); updated components and config mapping. |
| 2025‑10‑09 | v1.0 | Initial integrated software specification created from legacy architecture and model mapping. |
