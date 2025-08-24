# Changelog

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
