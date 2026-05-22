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
**Goal:** Introduce physically correct electrical topology: trains move along the network, and voltage drops follow distance via R_per_m track sections.

## Scope (updated for v0.8.M0)
- Dynamic electrical nodes:
  - Train node is created when entering the simulation and removed when leaving.
  - Train node carries `positionM` which is updated every tick.
- Track-section-based DC line:
  - Multiple contiguous track sections with `R_per_m`.
  - Each substation assigned a physical coordinate (meters along track).
  - Electrical distance between nodes computed via integration of `R_per_m`.
- Topology construction:
  - Nodes grouped by track and sorted by `positionM`.
  - Only adjacent nodes are connected in solver stamping.
- Solver update (MFE):
  - Replace fixed node IDs with dynamic per-device nodes.
  - Use PathResolver to compute resistances between adjacent nodes.
- Logging:
  - Log `node_id`, `positionM`.
  - Log segment boundaries / track section identifiers (optional).
  - Wide-files reflect dynamic node positions.

## Out-of-scope for M0 (future v0.8.x milestones)
- Converter-based limits (I_max, V_min, V_max).
- Full regenerative handling and local absorption constraints.
- “Nearest-train-wins” current distribution behaviour (requires converter model).
- Rail-return / dual-path extensions.

## Acceptance Criteria (updated)
- Voltage at a train varies correctly as its `positionM` changes along the line.
- A train at different positions yields different voltage drops for the same power.
- 3S2T-like scenarios show correct asymmetry based on spatial configuration.
- Wide-files include train node IDs and updated positions.
- All v0.7 tests pass unchanged, except where behaviour is intentionally more physical.


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
