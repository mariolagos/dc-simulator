# Progress Log

## 2025-08-21
- Refactored `TrainState` to include line-aware position (`int[]`).
- Restored `PositionUtils.parseFlexible` for handling flexible position formats.
- Rensat bort oanvända parsers och samplers (ProfilePositionSampler etc.).
- Simulation running again (DcSimApp v0.4).
- Next: Step B – train binding to nearest node (position & topology).

## 2025-08-20
- Introduced `NearestNodeTopology` for mapping positions to infrastructure nodes.
- Implemented basic `DcSimApp` main loop with profile binding.
- Output CSV now includes node voltages and power balances.

## 2025-07-28
- Iterative solver loop replaced with time-driven loop (`DcSimApp`).
- GridModel updated to handle both input structure and results.
- Version tagged as **v0.3**.

## 2025-07-18
- Completed Prototype 1A (Preprocessing).
- Added support for HOCON templates (`templates.conf`).
- TrainAggregatorApp produces aggregated timetable and power data.
