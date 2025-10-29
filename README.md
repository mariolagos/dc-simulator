# README.md – DcSim v0.4

## Purpose and Scope
DcSim is a simulator for DC-electrified railway systems that evaluates energy consumption, voltage stability, power flows, and losses in relation to train traffic. It supports realistic time-domain simulations, multiple trains, regenerative and resistive braking, and flexible electrical grid modeling.

The scope includes:
- Static DC electrical grid model
- Time-stepped simulation of trains using power profiles
- Handling of regenerative and resistive braking
- Export of results for post-processing and visualization

Typical use cases:
- Evaluating timetable changes
- Assessing new rolling stock
- Analyzing effects of adding/removing substations
- Studying changes in traction or braking performance

## 1. Main Components
1. **Electrical network**
  - Built from nodes, lines, substations, and connection points.
  - Substations modeled as EMF + internal resistance, connected between `groundNodeId` and a node.
  - Lines modeled as resistances.
  - Trains modeled as controllable power injections/absorptions.

2. **Time-domain simulation loop**
  - Iterates over simulation ticks.
  - Updates each train’s power request from its profile.
  - Solves node voltages and computes currents/powers.

3. **Power profiles**
  - Based on Excel `.xlsx` files.
  - Columns: `time [s]`, `bisPosition [km,m]`, `speed [m/s]`, `primaryMotoringPower [kW]`, `primaryMotorBrakingPower [kW]`.
  - Auxiliary power applied during station dwells; additional demand if `motoringAndAuxiliariesInSameModel = false`.

4. **Braking logic**
  - Regenerative braking feeds energy back if voltage is below the regeneration cut-off.
  - Above max voltage, braking energy is dissipated in resistors.

5. **Output data**
  - Voltages per node vs. time
  - Currents and powers per device vs. time
  - Train energy balances

## DC solver architecture & workflow

**Två sätt att bygga ett nät:**

1. **Production path**: `GridModel<Real>` → `DcNet` via `NetBuilder.makeNet(...)`
2. **Test/fixture path**: Skapa `DcNet` direkt med `NetFixtures`  
   (här fungerar `new DcNet(...)` som själva “makeNet”-steget).

**Kedja:**
- `MatrixBuilder.build(net, VseedOrNull)` bygger basmatrisen `G` och källvektorn `J`
  (linjer + substationers Norton-stämpling), utan jordklamp.
- `DcIterativeSolver.solveVoltages(net)` itererar icke-linjära delar (diodstationer, tåg),
  lägger små shuntar för numerisk stabilitet, klampar jord, och löser för `V`.

**Indexering:**
- `DcNet` använder kompakta index `0..n-1`.
- `groundIndex()` är index för jord.
- `indexById()` mappar original-ID → kompakt index.

**Produktionsflöde:**
```java
DcNet net = NetBuilder.makeNet(gridModel);
RealVector V = DcIterativeSolver.solveVoltages(net);
```

## 2. How to Run
Make sure you are in the root directory of the project.

### Using sbt
```bash
sbt "runMain dcsim.DcSimApp"
```

### Using IntelliJ IDEA
1. Open the project.
2. Locate `DcSimApp`.
3. Right-click and choose **Run**.

## 3. System Requirements
- Java 17 or newer
- Scala 2.13
- sbt
- Excel `.xlsx` files for load profiles

## 4. Configuration
`application.conf` defines:
- **track** – Stations and positions (format: `line km+m`)
- **grid** – Nodes, lines, substations (connected to ground), and connection points
- **traffic** – Timetables and train templates
- **powerProfiles** – Load profiles linked to train templates
- **simulationControl** – Start/end times, tick duration

Detailed format and examples: see **USER_GUIDE.md**.

## 5. References
- **USER_GUIDE.md** – Detailed configuration instructions
- **SoftwareSpecification.md** – High-level system design
-  USER GUIDE (dcSimulator)

This guide provides instructions for users of dcSimulator, including setup, configuration, and example case studies.

## Case Studies

The following examples are provided as **case studies**. They illustrate how to set up and run different simulation scenarios.  
New cases can be added as the project evolves.

| Case | Description | Purpose | Input files | Expected outcome |
|------|-------------|---------|--------------|------------------|
| 3subs1train | Three substations feeding one train | Illustrates basic DC supply with single train | `application.conf` (3 substations, 1 train) | Voltage profile along line; power balance |
| 3subs2train | Three substations feeding two trains | Demonstrates interaction between multiple trains | `application.conf` (3 substations, 2 trains) | Shared load, regenerative effects |
| symphony | Realistic multi-train setup (based on Symphony data) | Showcases system handling of timetable-driven trains | `application.conf` + timetable/power profiles | Aggregated load, timetable plots |

## Running a Case Study

1. Select the desired case study and corresponding input files.
2. Configure `application.conf` with grid, traffic, and power profiles.
3. Run the simulator using the main application.
4. Review output files (CSV, plots) for results.

---

Refer to [terms.md](terms.md) for all terminology used in this guide.

