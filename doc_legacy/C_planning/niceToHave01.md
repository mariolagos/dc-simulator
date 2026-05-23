# CR03 — Closed-Loop Train Controller (vmin/vmax)

**Status:** Open  
**Owner:** TBD  
**Impacted areas:** `DcSimApp` main loop (stamping), train profiles, solver step, tests  
**Goal:** Enable a mode where train power is limited by a controller that uses the current DC bus voltage (vmin throttle, vmax regen clamp).

---

## Background
Today, DcSimApp stamps train **P(t)** directly from profiles/timetables (open-loop, “profile mode”). In some scenarios we want **closed-loop** behaviour: reduce traction near **vmin** and block regenerative backfeed at/above **vmax**, so network results remain realistic and stable.

## Scope (MVP)
- **Feature flag (default OFF):** `-Ddcsim.closedLoop=true` enables closed-loop.
- **Controller API:** `TrainController.applyLimits(Preq, V, vmin, vmax) -> Papplied`.
    - Reference implementation: `SimpleTrainController(vminThrottleBandFrac)`.
- **Integration point (before stamping each step):**
    1. Read bus voltage `V` for the train’s node (from last solution / initial guess).
    2. Compute `Papplied = controller.applyLimits(Preq, V, vmin, vmax)`.
    3. Stamp `Papplied` into KCL (e.g., Norton: `I = -Papplied / max(V, ε)`).

- **Config:** `vmin`, `vmax`, `vminThrottleBandFrac` (e.g., app config or scenario file).

## Out of Scope (MVP)
- No change to existing profile formats or Excel export.
- No UI; controlled via JVM property.

## Design Notes
- **Sign convention:** `+P = traction / load`, `−P = regen / source`.
- **Numerics:** Protect against `V ≈ 0` with `ε ≈ 1e−9` when computing `I = −P/V`.
- **Performance:** Reuse the existing solver/build; closed-loop adds only a small per-train step.

## Test Plan
**Unit (already present):**
- `motor_throttled_near_zero_by_vmin`
- `regen_blocked_above_vmax_with_diode_substation`
- `SimpleTrainControllerEdgeEpsilonTest`

**Integration (new):**
- `ClosedLoop_off_defaults_to_profile`: with `-Ddcsim.closedLoop=false`, results equal open-loop.
- `ClosedLoop_on_blocks_regen_above_vmax`: verify near-zero backfeed at high V.

**CI:**
- Keep smoke unchanged (open-loop).
- Run closed-loop tests in full suite with explicit flag.

## Rollout
1. Land controller + flag (default OFF).
2. Add integration tests (closed-loop), not part of the smoke job.
3. Add a short “How to enable closed-loop” snippet to `README_dev.md`.

## Risks & Mitigations
- **Boundary jitter around vmin/vmax:** use epsilon band and deterministic tolerances in tests.
- **Accidental enablement in CI:** default OFF + separate CI step that sets the flag.
- **Sign-convention mismatch:** concise docs + assertions in tests.

## Acceptance Criteria
- **Open-loop (default):** bit-identical results to current pipeline.
- **Closed-loop:** expected throttling/clamping in targeted scenarios.
- **CI:** smoke green; full suite green with and without the flag.

## Implementation Sketch (pseudo)
```java
for (t in timeSteps) {
  // ... build static stamps ...
  for (train : trains) {
    double Preq = profiles.getPower(train, t); // open-loop input
    double Puse = Preq;
    if (closedLoop) {
      double V = grid.nodeVoltage(train.nodeId()); // from last solution
      Puse = controller.applyLimits(Preq, V, cfg.vmin, cfg.vmax);
    }
    double Vsafe = Math.max(1e-9, grid.nodeVoltage(train.nodeId()));
    double Iinj = -Puse / Vsafe; // Norton
    stampCurrentInjection(train.nodeId(), Iinj);
  }
  solve();
  // export as today
}
```
