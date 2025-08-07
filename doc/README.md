# DC-simulator

This simulator evaluates energy consumption and grid interaction for train traffic on DC-electrified railways. It supports time-dependent simulations, multiple trains, and realistic electrical modeling.

## Purpose and scope

The purpose is to simulate how train traffic interacts with the electrical infrastructure in terms of power flows, voltage stability, losses, and energy usage. The model is designed to analyze scenarios such as:

- Timetable changes
- New rolling stock
- Additional or removed substations
- Changes in traction performance or braking characteristics

The scope includes:
- A static DC electrical grid model
- Time-stepped simulation of trains with power profiles
- Regenerative braking and resistive braking handling
- Result export for post-processing and visualization

## Main components

### 1. Electrical network
Represented by a nodal admittance matrix built from the system topology. Devices supported:
- Substations (modeled as EMF with internal resistance)
- DC lines (modeled as resistance)
- Trains (power-controlled, can inject or absorb current)
- Optional: Braking resistors (internal to train model)

### 2. Time-domain simulation loop
Iterates over time steps and:
- Updates power request for each train based on its profile
- Stamps the admittance matrix
- Solves for node voltages
- Computes currents and powers for all devices

### 3. Power profiles
Each train uses a `PowerProfile` consisting of `PowerPoint` entries:
- time [s]
- position [km+m]
- speed [m/s]
- power [W]

Interpolated using splines to give continuous power request.

### 4. Braking logic
When trains brake, energy is:
- Fed back to the line if voltage < cutoff level
- Sent to internal brake resistor if voltage > max level
- Split proportionally in between

### 5. Output data
Simulation results are exported as:
- Voltages per node vs. time
- Currents and powers per device vs. time
- Detailed train power balance:
  - Power from line
  - Power to brake resistor

## Running the simulation
1. Prepare timetable and power profiles in Excel format.
2. Define the grid model in a `.conf` file.
3. Run simulation:
```bash
java -cp target/dc-simulator.jar org.dcsim.MainLoop input/application.conf
```

4. Output files are generated in `output/` directory.

## Visualization
Separate tools are used for plotting:
- Timetable plots
- Aggregated power plots
- Voltage profiles
- Braking current and energy balance

## Status
Version: 0.4
- Prototype for DC simulation complete
- Supports multiple trains
- Verified energy conservation

Next steps:
- AC network modeling
- Integration with timetable planner
- More realistic substation and train dynamics

---
(c) Railway Simulation Project, 2025
