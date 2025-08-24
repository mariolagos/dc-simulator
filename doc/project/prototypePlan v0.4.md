# Prototype Plan v0.4

## Vision
The project aims to develop a **DC railway power system simulator** ("DcSim") capable of handling multi-train scenarios with dynamic topology, AC/DC conversion, and realistic load-flow calculations.  
The simulator should serve both as a **research tool** and as a **basis for industrial applications**.

---

## Prototype Roadmap

### Prototype 1A – Preprocessing
- **Goal:** Aggregate timetables and power profiles from templates.
- **Status:** ✅ Completed.
- **Outcome:** Functioning TrainAggregatorApp with timetable expansion and profile parsing.

### Prototype 1B – DC Simulation
- **Goal:** Introduce grid model and DC solver. Simulate power flow with simple train loads.
- **Status:** 🔄 In progress (v0.4).
- **Outcome so far:**
    - Grid model structure and devices (substations, trains, lines).
    - Iterative solver loop in `DcSimApp`.
    - Results written to CSV.
    - First trains connected via profiles.

### Prototype 2 – Extended Position & Topology
- **Goal:** Connect trains dynamically to nearest nodes, support sectionalization.
- **Planned work:** Use `NearestNodeTopology` and `PositionUtils` to track movement across nodes.

### Prototype 3 – Multi-train Dynamics
- **Goal:** Handle multiple trains on same line, with bidirectional movement and time-driven interaction.
- **Planned work:** Event-driven state machine (potentially with Akka).

---

## Next Steps
1. Finalize **Prototype 1B** with functional train-to-node binding (Step B).
2. Extend CSV results with power per train.
3. Prepare version 0.5 release.  
