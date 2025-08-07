# DC Simulator

## Overview
This simulator models train movement and power demand over time, producing time series outputs that can be used to analyze energy flow in railway systems.

The current version (v0.2) includes:
- Simple point-mass train movement with acceleration, cruise, and braking phases.
- PowerPoint time series output per train.
- Excel export per train.
- Modular structure to enable integration with electrical network models.

---

## Modules

### ✅ `MainLoop`
Orchestrates simulation of trains. Each train starts at a defined time and position, accelerates, cruises, and brakes to stop.

### ✅ `PowerPoint`
Immutable record representing one time sample:
```java
record PowerPoint(
    double time,
    String position, // in "s km+ m" format
    double speed,
    double power,
    double voltage,
    double current
)
```

✅ SimpleExcelExport

Exports a list of PowerPoints to .xlsx. Handles SI and non-SI formats.
✅ PositionUtils

Provides conversion between string and numerical position formats.
Coming Module: Electrical Model

We will now implement a power network simulation layer. Two solution strategies will be supported:
1. Nonlinear Power Flow

    Solve full power balance equations at each node.

    Input: PowerPoint per train.

    Output: voltage per node, current and losses per branch.

2. Linear Iterative Approximation

    Initially assumes voltages (e.g. 750 V flat), computes currents.

    Update voltages based on impedance and losses.

    Iterate.

Generalization with FieldElement

    Enables use of Real for DC systems.

    Future upgrade: Complex for AC networks.

    All simulation algebra is field-agnostic.

Planned Classes

    Node: voltage unknowns.

    Branch: resistance/impedance.

    Device: e.g. Train, Substation.

    Solver: executes network solution.

Versioning

    ✅ v0.1: Preprocessor and profile generator.

    ✅ v0.2: Basic train movement simulator with Excel export.

    🔜 v0.3: Electrical DC power flow simulation.

Developer Notes

    All code is currently in package org.dcsim.

    No external dependencies except Apache POI.

    Internal tools: PositionUtils, TimeUtils (can be expanded).

Running the Simulation

    Compile the code (Java 17+).

    Run MainLoop.

    Output will be written to output/Train_*.xlsx.

License

TBD.
Author

This codebase is collaboratively developed by the user and ChatGPT for simulation of realistic train and power network interactions.

