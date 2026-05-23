# Changelog

## v0.8 — Node model & track-section topology

**Status:** Inception → M0 implementation underway  
**Focus:** Dynamic power nodes, line segmentation, position-based load sharing

---

### Implemented in v0.8.M0 (delta)

#### Added
- Introduced a physically correct **one node per device** model (substations, trains, ground).
- Added node metadata fields:
  - `nodeKind` (`SUBSTATION`, `TRAIN`, `GROUND`)
  - `trackId`
  - `positionM` (meters along the line)
- Added **R_per_m** to track-section configuration and the corresponding segment model.
- Added **PathResolver**: computes line resistance between two positions using the underlying
  track sections.
- Added dynamic adjacency building in GridModelActor:
  - active nodes are grouped by track,
  - sorted by position,
  - consecutive nodes are connected with resistors derived from track geometry.

#### Changed
- Network assembly now uses **geometric distance** between nodes to build the DC matrix.
- Train nodes are created dynamically when trains enter the line and removed when they leave.
- Train node position is updated each tick to maintain correct impedance calculations.

#### Not changed (compatibility preserved)
- Solver API and matrix structure remain unchanged.
- Node indices and indexing logic remain intact.
- Internal models for substations and trains (EMF, internal resistance, Norton equivalents)
  remain unchanged.

#### Configuration impact
- Track sections must now include an `R_per_m` field.
- Substations must specify an explicit position along the line.

---

### Planned (high priority, later v0.8 milestones)

*(These items originate from the earlier planning notes and remain fully valid.
Nothing removed.)*

- Introduce **dynamic electrical nodes** following train position  
  *(partially fulfilled in v0.8.M0 for topology; future work involves richer metadata + events).*
- Implement **segmented line model** with distributed impedances  
  *(v0.8.M0 covers electrical R-per-meter; spatial return modelling remains future work).*
- Replace heuristic derating with **converter-based limits**  
  (`I_max`, `V_min`, `V_max`, ramps, absorption constraints).
- Enable physically correct **“nearest-train-wins”** current distribution.
- Redesign solver stamping for moving topology  
  *(M0 adds adjacency stamping; further work includes incremental stamping and caching).*
- Rewrite and integrate **softwareSpecification.md** for v0.8 architecture.

---

### Planned (secondary)

- Add test scenarios:
  - Dynamic **3S2T**
  - **Multi-train acceleration conflict**
  - **Regenerative absorption edge cases** with spatial separation
- Performance improvements for multi-hour simulations with >50 trains.
- Optional extension: **rail-return modelling**.

---

## [v0.7] – Stabiliserad prototyp (2025-11-20)

**Status**: Completed / Frozen
**Focus**: Robusthet, logging, heuristik för derating

### Added

- Voltage-based traction derating heuristic (alpha(V)) to prevent solver divergence under deep undervoltage.
- Canonical req_W pipeline (NET-stage) for consistent load representation across GMA and solver.
- Metadata propagation to longtable and wide files (project, scenario, git hash).
- Regen limiting improvements (braking → net absorption → resistor fallback) with diode OVP.
- Stabilized longtable writer integration and safe single-pass metadata emission.

## Improved

- Solver stability under extreme traction demand (1sub5t scenario).
- Traceability between physical requests, solver stamps, and output signals.
- Train/Substation wide-file grouping for post-analysis.
- Behavioural correctness in 3S2T regen scenarios.

## Known limitations (acceptable for v0.7)

- All trains share a static electrical node; no spatial distribution effects.
- Heuristic derating replaces physical converter behaviour.
- No dynamic line segmentation; impedances compressed.
- No OPF-like decision layer (by design).
- sS.md remains at v0.6 technical depth; updated only after v0.8 architectural changes.

## Rationale

v0.7 fulfills its intended role:
A robust, traceable, end-to-end functioning simulator capable of revealing network weakness (voltage collapse, regen blocking, loss patterns) under multi-train load.

Further refinement is blocked by the static electrical topology.
A shift to v0.8 is required before more realistic physics can emerge.

## [0.4] - 2025-08-21
### Added
- TrainState now line-aware (int[] position).
- NearestNodeTopology for infrastructure nodes.
- Restored `PositionUtils.parseFlexible`.

### Removed
- Unused parser and sampler classes (e.g., ProfilePositionSampler).

### Fixed
- DcSimApp main loop now stable after refactor.

---

## [0.3] - 2025-07-28
### Changed
- Iterative solver loop replaced with time-driven loop.
- GridModel extended to hold results as well as structure.

---

## [0.2] - 2025-07-22
### Added
- PowerProfile with Excel-based input.
- ResultCsvWriter producing electrical.csv output.

---

## [0.1] - 2025-07-11
### Added
- TrainAggregatorApp with timetable expansion.
- Support for station positions and signatures.
