# DC-Simulator Milestone Plan (v0.7 → v0.9)

The milestone plan converts the high-level roadmap into concrete, deliverable steps with clear acceptance criteria.

---------------------------------------------------------------------

# v0.7 — Code Cleanup + Voltage Ramp
**Goal:** Establish a clean, stable and deterministic simulation core with correct low- and high-voltage behaviour.

## Scope
- Removal of legacy code paths (GridModelActor, DcIterativeSolver, AdapterSolver)
- Removal of old probe/debug branches
- Consolidation of TrainLoad into one clean power interface
- Stable naming conventions for all logged signals
- Implementation of voltage derating:
    - V_nom (nominal voltage)
    - V_bmin1, V_bmin2 (low-voltage derating ramp)
    - V_max (high-voltage derating / overvoltage protection)
- New signal: traction_derate_factor (0.0–1.0)
- Updated traction logic: motoring = motoring_raw * derate_factor
- Updated regen acceptance rules if needed

## Acceptance Criteria
- Simulator runs 3S1T and 3S2T without any legacy code paths active
- Voltage ramp scales motoring power continuously between V_bmin2 → V_bmin1 → V_nom
- Regen behaviour identical to v0.6 when voltages are nominal
- Wide-files include new signals
- All classes compile without warnings related to unused code
- Documentation updated: signal dictionary + ramp description

---------------------------------------------------------------------

# v0.8 — Train Movement + Multi-Section DC Line
**Goal:** Introduce physical realism: trains move along the network, and voltage drops depend on distance.

## Scope
- Dynamic electrical node assignment: train node follows position
- Helper: getNearestElectricalSection(position)
- Basic S–D–T hooks (optional): mapping position → track section
- Segmented DC line:
    - Multiple line segments with R_per_km
    - Each substation assigned to physical coordinate
    - Train calculates actual distance to each substation
- Solver update:
    - Replace hardcoded nodes (e.g., “4”) with dynamic node IDs
    - Multi-section voltage drop calculation
- Logging:
    - Train.node_id
    - Section IDs
    - Distance-based voltage effects

## Acceptance Criteria
- Train voltage follows physically correct behaviour when moving away/toward feed points
- A train at different positions produces different voltage drops for the same power
- 3S2T scenario shows correct asymmetry depending on train positions
- Wide-files include node_id and section_id
- All tests from v0.7 remain valid

---------------------------------------------------------------------

# v0.9 — Scenario Management + Improved Outputs
**Goal:** Make the simulator usable for systematic studies, automation, and clean reporting.

## Scope
- Project/scenario directory structure
- Scenario config validation (schema or validator class)
- Metadata: project, scenario, version, hash
- Batch execution mode
- Improved wide-file structure:
    - Consistent grouping of signals
    - Metadata sheet
    - Signal dictionary auto-generated
- Optional:
    - PDF auto-report (longtable → wide → PDF summary)
    - Plot presets (train voltage, substation power, regen behaviour)

## Acceptance Criteria
- New scenario can be created by copying a template directory
- Simulator runs multiple scenarios sequentially in batch mode
- All outputs include metadata (project, scenario, version, time)
- Wide-files are stable: fields never randomly added/removed
- User can run a scenario and obtain:
    - CSV longtable
    - Wide-file
    - PDF summary (optional)
    - Plots (optional)

---------------------------------------------------------------------

# End of Milestone Plan
