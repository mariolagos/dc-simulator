# Basic bus models

This document summarizes the basic components used to model a railway traction power system using a DC simulator framework.

## Substations

- Represented as ideal EMFs with internal resistance.
- Only supply power when voltage at feeding node is below EMF.
- No backfeed allowed (diode behavior).

## DC Lines

- Modeled as resistive elements.
- Resistance is proportional to line length.
- Power loss computed as I²R.

## Trains

- Each train is modeled as a two-port power-controlled device.
- Supports both traction and regenerative braking modes.
- Implements transition between line regeneration and braking resistor.
- Power request is based on a spline-interpolated time-dependent profile.

## Braking Logic

- Regeneration to the line occurs if voltage < cutoff voltage (e.g., 850 V).
- Above maximum voltage (e.g., 1000 V), all braking power is sent to the resistor.
- Between these thresholds, power is split linearly between line and resistor.

## Output Variables

- Node voltages (per timestep).
- Device currents and powers (substations, trains, lines).
- Power balance components for trains:
  - Line power
  - Brake resistor power
- All outputs can be exported to CSV and/or Excel for analysis.
