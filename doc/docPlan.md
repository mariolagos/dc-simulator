# Documentation Plan for DC-Simulator Project

This document summarizes the written documentation produced for the DC-simulator project. It identifies key files, their purposes, and their current status.

---

## 1. README / README2

### Purpose
Developer documentation and project introduction.

### Content
- Project structure
- Build and run instructions
- Data model definitions (`TrainTimetable`, `PowerPoint`, etc.)
- Example configurations
- Implementation notes and utilities

### Status
- Multiple versions
    - `README`: general documentation
    - `README2`: focused on "Basic bus models"

---

## 2. softwareSpecification

### Purpose
Functional and technical specification.

### Content
- Vision and objectives
- Architecture and component responsibilities
- Use of Akka for event-driven simulation
- Requirements for AC/DC simulation
- Dynamic topology handling

---

## 3. Presentation Outline (PowerPoint)

### Purpose
Presentation material for stakeholders.

### Content
1. Project scope and goals
2. Electrical network model
3. Simulation structure
4. Braking and regeneration behavior
5. Simulation results and analysis
6. Plots from `MinimalTest`
7. Power balance (line, train, braking resistor)
8. Achievements and next steps

### Status
- Exported as `DC-simulator-presentation.pptx`
- Extended with simulation plots and Excel/CSV outputs

---

## 4. Patch/version management notes

### Purpose
Describe the synchronization and versioning strategy.

### Content
- Use of zip-archives for full snapshots (e.g., `v0.2`, `v0.4`)
- Patch-based updates for consistent sync
- Git commit structure and rollback points

---

## Summary
The above documents collectively describe:
- The simulator design and purpose
- How to use and extend the system
- Key results and presentation material
- Version control methodology

Next step: bundle all files into a single archive for distribution or archival.
