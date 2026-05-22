# SoftwareSpecification.md – DcSim v0.3

## Purpose
This document outlines the functional and non-functional requirements for the DcSim railway DC traction power simulation software. It references the **USER_GUIDE.md** for all detailed configuration structures and examples to avoid duplication.

## 1. Scope
DcSim simulates DC railway traction systems, including:
- Moving trains following defined timetables and load profiles.
- Electrical grid models with nodes, lines, substations, and connection points.
- Power flow calculations at each simulation tick.

## 2. References
- **USER_GUIDE.md** – Contains detailed configuration syntax, examples, and parameter explanations.
- **README.md** – Provides developer instructions.

## 3. High-Level Functional Requirements
1. Load configuration from `application.conf`.
2. Initialize track, grid, traffic, and power profile subsystems.
3. Run simulations according to `simulationControl` parameters.
4. Output results for analysis and optional plotting.

## 4. Simulation Workflow
1. Parse `application.conf` (see USER_GUIDE for structure).
2. Create models:
   - Track stations and positions.
   - Electrical grid with nodes, lines, substations, connection points.
   - Traffic (train timetable and templates).
   - Power profiles per train.
3. Time iteration:
   - At each tick (from `tickDuration`), update train positions and loads.
   - Solve the electrical system.
   - Store results.

## 5. Non-Functional Requirements
- Java 17+, Scala 2.13, sbt.
- Compatible with `.xlsx` load profile files.
- Modular architecture for extension.

## 6. Constraints
- Large datasets must be handled efficiently.
- Accurate time-step simulation.

## 7. Notes
- For configuration examples and field-level descriptions, **always refer to USER_GUIDE.md**.
- Any change in configuration format must first be updated in USER_GUIDE.md.
