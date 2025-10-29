# terms.md
**Document Level:** A (User / Model Documentation)  
**Purpose:** Central glossary for DcSimulator – physical, numerical, and software terms.  
**Audience:** Users, analysts, and developers.

---

## Version History

| Date | Version | Notes |
|------|----------|-------|
| 2024‑10‑01 | v1 | Initial glossary extracted from system design. |
| 2025‑04‑15 | v2 | Added core simulation, topology, and AC/DC modelling terms. |
| 2025‑10‑09 | v3 | Added numerical and actor terms (SolverConfig, EPS, relaxation factor, DcIslands, etc.). |

---

## Domain Terms (Electrical / Physical)

| Term (EN) | Term (SV) | Definition |
|------------|------------|-------------|
| **Substation** | Matningsstation | Converts AC to DC and injects current into the traction line. |
| **Train** | Tåg | Electrical traction load or generator moving along the track. |
| **Catenary / Line** | Kontaktledning | DC traction supply line connecting nodes. |
| **Node** | Nod | Electrical connection point in the network. |
| **Ground** | Jord | Reference potential (0 V). |
| **Voltage (V)** | Spänning | Electric potential difference between nodes. |
| **Current (I)** | Ström | Flow of electric charge through a branch. |
| **Resistance (R)** | Resistans | Opposition to current flow, in ohms. |
| **Conductance (G)** | Ledningsförmåga | Reciprocal of resistance. |
| **Power (P)** | Effekt | Product of voltage and current. |
| **Leak / Shunt Conductance** | Läcka / Shuntkonduktans | Parallel conductance to ground for numerical stability. |
| **Clamp (diode)** | Kläm-diod | Prevents reverse current flow from the line to a substation. |
| **Clamp state** | Klämtillstånd | Logical state indicating whether the diode conducts. |
| **Island** | Ö | Electrically disconnected region of the network without feed. |
| **Seed (voltage)** | Startspänning | Initial voltage used to start numerical iteration. |
| **Relaxation factor** | Relaxationsfaktor | Numerical damping parameter in iterative solver. |
| **Stamping** | Stämpling | Process of inserting component equations into the system matrix. |
| **Stamp** | Stämpel | A single contribution (ΔY, ΔJ) from a device. |
| **Section table** | Sektionstabell | Track segmentation table used for position-to-node mapping. |

---

## Software and Solver Terms

| Term (EN) | Term (SV) | Definition |
|------------|------------|-------------|
| **SolverConfig** | Lösarkonfiguration | Central configuration for EPS, shunts, relaxation, and iteration control. |
| **EPS (tolerance)** | EPS (tolerans) | Minimal numeric threshold used to prevent division by zero or singularities. |
| **DcIslands** | DC‑öar | Computed subgraphs representing disconnected electrical regions. |
| **SeedRelaxationCycle** | Initieringscykel | Combined seeding and relaxation iteration phase at solver start. |
| **GridSolver** | Nätlösare | Main numerical engine computing node voltages. |
| **ReporterActor** | Rapportaktör | Component that logs voltages, currents, and power per simulation tick. |
| **SimulationController** | Simulationskontroller | Actor responsible for time control and event sequencing. |
| **Solver iteration** | Lösariteration | A single pass computing updated voltages until convergence. |
| **Convergence** | Konvergens | Condition where successive voltage updates differ less than EPS. |
| **Solver leak** | Lösarläcka | Artificial conductance added to ensure matrix invertibility. |
| **Paced mode (REAL_TIME)** | Realtidsläge | Simulation paced to wall-clock time for demos. |
| **FAST mode** | Snabbläge | Simulation running as fast as possible for batch results. |

---

## Event and State Terms

| Term (EN) | Term (SV) | Definition |
|------------|------------|-------------|
| **Event** | Händelse | A scheduled trigger (e.g., Tick, TrainStart, SolveTick). |
| **Tick** | Tidsteg | Fundamental simulation interval; one discrete update of all actors. |
| **SolveTick** | Lösar‑tick | Event calling the electrical solver each step. |
| **TrainStart** | Tågststart | Event when a new train enters the simulation. |
| **TrainStop** | Tågstopp | Event when a train finishes or leaves the line. |
| **State** | Tillstånd | Logical condition of a component or actor (Idle, Running, Paused, Finished). |
| **Transition** | Övergång | Change between states triggered by events. |
| **Actor** | Aktör | Independent concurrent entity (TrainActor, GridModelActor, ReporterActor). |

---

## Mathematical Symbols

| Symbol | Meaning | Typical value |
|---------|----------|---------------|
| **E** | Substation EMF | 750 – 3000 V |
| **Rint** | Internal resistance | 0.05 – 0.2 Ω |
| **G_leak** | Leak conductance | 1e‑5 S |
| **EPS** | Numerical tolerance | 1e‑9 |
| **Vmin_train** | Train cutoff voltage | 600 V |
| **TickDuration** | Simulation time step | 0.1 – 1 s |

---

## Cross‑References

- See `modelDescription.md` for mathematical and electrical formulation.
- See `softwareSpecification.md` for implementation mapping.
- See `README_dev.md` for developer integration notes.

---

_This glossary is the authoritative source for terminology consistency across all DcSimulator documentation._
