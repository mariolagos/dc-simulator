# USER GUIDE (dcSimulator)
**Document Level: A (User)**  
**Purpose:** Practical guide for installing, configuring and running simulations.

---

## 1. Introduction
Key points extracted from legacy guide:
- DcSim Electrical Model Overview – v0.4
- Purpose
- The purpose of DcSim is to simulate how train traf fic interacts with the electrical infrastructure in
- DC electrical modeling, multiple trains, time-stepped simulation, and detailed energy flow
- DcSim Electrical Model Overview
- DC Lines
- The configuration is defined in application.conf  under the dcsim  root. Below , each section
- Purpose: DcSimApp  runs the simulation based on your configuration file.

Refer to `terms.md` for definitions (English with Swedish equivalents).

---

## 2. Installation & Run Environment
Minimum recommended environment:
- Java 17+ (or project target runtime)
- Read/write permissions for input/output paths

### 2.1 Run Modes
- FAST: batch-oriented, runs as fast as possible.
- REAL_TIME: paced against wall-clock for demonstrations.
  Legacy notes:
- simulationStart  (String)
- simulationStart = "2025-08-11T08:00:00+02:00"
- 6. How to Run
- Purpose: DcSimApp  runs the simulation based on your configuration file.
- Using sbt:
- Usagesbt runMain dcsim.DcSimApp

---

## 3. Basic Usage
1) Prepare `application.conf` (grid, traffic, powerProfiles, simulationControl).
2) Choose a case study (Section 6).
3) Run the simulator (launcher/IDE).
4) Inspect CSV outputs and plots.

---

## 4. Configuration Reference (HOCON)
All configuration lives under `dcsim.*` in `application.conf`.

### 4.1 Grid (`dcsim.grid`)
- groundNodeId – id of the 0 V reference node
- nodes – list of nodes (ids, optional metadata)
- lines – resistive connections (Ω or Ω/km + length)
- substations – EMF + Rint, diode behavior (no backfeed)

### 4.2 Traffic (`dcsim.traffic`)
- trains – instances with start times and template refs
- positions – anchor nodes or longitudinal positions (km+m)

### 4.3 Power Profiles (`dcsim.powerProfiles`)
- templates – reusable profiles (motoring, braking, auxiliaries)
- sources – Excel/CSV files and interpolation details

### 4.4 Simulation Control (`dcsim.simulationControl`)
- tickDuration – step size in seconds
- simulationStart / simulationEnd – window (seconds-of-day)
- mode – FAST or REAL_TIME

Legacy configuration lines from PDF:
- The configuration is defined in application.conf  under the dcsim  root. Below , each section
- 2. grid
- grid {
- 3. traffic
- 4. powerProfiles  ]
- traffic {
- 5. simulationControl
- powerProfiles {
- simulationControl {
- Usagesbt runMain dcsim.DcSimApp
- Place application.conf  at project/<project_name>/ .

---

## 5. Results & Visualization
Typical outputs:
- CSV with `time`, `step`, `V(node)`, `P[...]`, aggregates (`P_trains`, `P_substations_out`, `P_lines`, `P_brake`).
- Optional long table for pivoting in Excel:

    time | object | signal | value
      -----|--------|--------|------
    12.0 | Train_1 | P_req | 5000
    12.0 | Train_1 | P_delivered | 4800
    12.0 | Substation_A | Voltage | 740

Open in Excel and insert a PivotTable to slice by object, signal, and time.

Legacy output notes from PDF:
- terms of power flows, voltage stability , losses, and energy usage. The model supports realistic
- Supply power only when voltage at feeding node is below EMF .
- Output Variables
- Example:Regeneration to line if voltage < regeneration cutof f (e.g., 850 V).
- Above maximum voltage (e.g., 1000 V), all braking power goes to resistor .
- Node voltages per timestep.
- All outputs exportable to CSV and/or Excel.
- Excel .xlsx  power profile files

---

## 6. Case Studies (Examples)

    Case         | Description                                 | Input files                             | Expected outcome
    -------------|---------------------------------------------|-----------------------------------------|------------------
    3subs1train  | Three substations feeding one train         | `application.conf` (3 subst., 1 train)  | Voltage profile; power balance
    3subs2train  | Three substations feeding two trains        | `application.conf` (3 subst., 2 trains) | Shared load; regenerative effects
    symphony     | Realistic multi-train timetable-driven case | `application.conf` + profiles/timetable | Aggregated load; timetable plots

---

## 7. Troubleshooting
- Empty CSV: check output folder and flush-on-tick.
- Strange voltages: verify ground, EMF/Rint, line units (Ω vs Ω/km).
- Instability: lower tickDuration; verify profile interpolation.
- Regen missing: confirm diode clamp & thresholds; check P_brake rows.

---

## 8. Appendix
### 8.1 Folder layout (example)

    docs/
    ├── A_user/
    │   ├── USER_GUIDE.md
    │   ├── modelDescription.md
    │   ├── terms.md
    │   └── examples/
    ├── B_developer/
    │   ├── README_dev.md
    │   ├── softwareSpecification.md
    │   ├── testPlan.md
    └── C_planning/
        ├── docPlan.md
        ├── prototypePlan.md
        └── progressStatus.md

### 8.2 Terminology
See `terms.md` for glossary and conventions.

---

_This USER_GUIDE.md was reconstructed from legacy USER_GUIDE.pdf and normalized for portability._