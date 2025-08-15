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
