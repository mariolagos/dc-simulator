
# Software Specification

## Purpose
Defines how the DC-simulator is structured, configured, and run, for both developers and advanced users.

## File Structure
- `src/` - Java source code
- `input/` - Configuration files (`.conf`) and Excel profiles
- `output/` - Result files (CSV, XLSX)
- `project/` - Optional project-specific data

## Configuration
The simulator is configured using HOCON (`application.conf`). Key sections:

### dcsim.grid
- `nodes`: Each with `id`, `position`, and optional `voltage`
- `lines`: With `id`, `from`, `to`, `length`, `r`
- `substations`: EMF source with internal resistance
- `trains`: Train instances, each with `template` and `departure`
- `templates`: Predefined train route templates

### dcsim.powerProfiles
- `templateDirectory`: Location of Excel-based templates

## Execution
Run using:

```bash
java -cp target/dc-simulator.jar org.dcsim.MainLoop input/application.conf
```

Supports:
- Time-stepped simulation
- Per-train power updates
- Voltage solving
- Output per device and node

## Java Interfaces

### `Device<Real>`
Represents a grid-connected electrical device. Key methods:
- `computeCurrent(...)`
- `getPower(...)`
- `stamp(...)` for matrix contribution

### `ElectricSolver`
Solves the nodal voltage system from the model.

## Output
Generated in `/output`:
- `electrical_output_0.xlsx` — node voltages, currents, powers
- `*.csv` — additional current/power breakdowns (e.g., braking)

## Developer Notes
- Uses Apache Commons Math
- Extendable to AC in future
- Use consistent units (V, A, W, Ohm)

