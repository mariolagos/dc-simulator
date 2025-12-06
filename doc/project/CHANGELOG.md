# Changelog

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
