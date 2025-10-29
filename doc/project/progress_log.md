# Progress Log

## 2025-08-31
- Reframed substation model and stamping to a consistent Norton form.
- Clarified **polarity**: `dV = Va - Vb` with `a = from (bus)`, `b = to (return/ground)`. 
- Adopted nodal injection convention for a current source from `a → b`: **-I at a, +I at b**.
- Implemented diode/backfeed logic:
  - If `allowBackfeed=false`: active only when `Va - Vb < E` (forward). Otherwise, tiny `G_leak` for stability.
  - If `allowBackfeed=true`: always active (can absorb power).
- Added optional **per-station current limit** `maxCurrentA` (clamped on net Norton current after diode rule).
- Train braking split: fraction of negative power goes to line vs. brake resistor based on |ΔV| and (cutoff,maxV).
- `GridResult` extended with node voltages, per-device currents, powers, and requested power tracking.
- `GridModelLoader` unified orientation: substations are created as `(from=bus, to=ground)` and backfeed default read from config.
- `GridModelActor` backpressure reply so `Root` can tick only after CSV row is flushed.
- Created **Test A0 (open circuit)** config to validate that with no lines and no trains:
  - `V(bus) ≈ E`, `I[S] ≈ 0`, `P[S] ≈ 0`, losses≈0.

## Known Issues / Notes
- If you see `V(bus)` wrong or huge substation power in A0, check stamping signs:
  - Solver substation injection **must** be `J[a] += -I_src; J[b] += +I_src`.
  - Substation.stamp must mirror the same sign convention.
- Power accounting in the CSV should not double count internal losses. Network balance uses:
  - `P_substations_out (sum over stations) + P_trains (signed) = P_lines (losses) + Mismatch`
- Spline interpolation for plotting is a TODO (post-processing only).