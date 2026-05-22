# PrototypePlan.md v0.5

This plan collects the short-term roadmap for prototyping features and validating the electrical simulation
loop.

## Current Scope (v0.4)
- DC nodal solver with substations (EMF + Rint, diode/no backfeed).
- Resistive lines, I²R losses, fixed grid topology.
- Train modeled as two-terminal power device with braking logic and requested/actual power tracking.
- CSV export with node voltages, per-device powers, requested train power, brake power, and aggregates.

## Validation Status
- **Voltages:** consistent per node; sign convention under review (prefer positive node potentials;
presentation layer can flip if needed).
- **Substations:** no backfeed; positive delivered power when feeding.
- **Lines:** losses ≥ 0; quadratic with current.
- **Trains:** requested vs delivered power tracked; brake resistor power reported via pseudo-device.
- **Aggregates:** mismatch = `P_substations_out - P_trains - P_lines` ~ 0 (network conservation).  
  (Planned: add `P_lack = P_req - P_trains` to quantify shortages/receptivity limits.)

---

## Next Steps: Moving Train Node & Dynamic Topology (plan)

### Goal
Make the electrical coupling point of each train move along the track with its physical position, updating
the grid topology and line resistances as the train travels.

### Approach
1. **Position → segment lookup**
    - Maintain a spatial index of line segments with cumulative lengths.
 - Given `positionMeters`, locate the acti
    ve segment `(nA → nB)` and the fractional distance `f ∈ [0,1]`.

> I suppose that ** spatial ** means to handle a network instead of the particular case consisting of a
> single line

2. **On-the-fly split (virtual node)**
    - Introduce a transient node `nT` at the split.
    - Replace line `(nA → nB, R)` with two lines:
        - `(nA → nT, R * f)` and `(nT → nB, R * (1 - f))`.
    - Attach `TrainLoad` between `nT` and ground.

> Transient nodes are introduced at train departures and deleted when trains arrival to end station. I don´t
> see the need to handle only one train in the simulation. the question is to define ad hoc dynamic lists.
> The problem to find distances shall be delegated to PositionUtils.
> it should also be necessary to change configuration file by adding a train parameter: conductor at each
> leg: catenari1, catenary 2, etc.

3. **Hysteresis & stability**
    - Only re-split when the train crosses a *movement threshold* (e.g., ≥ 25–50 m) to avoid churn.
    - Reuse `nT` if the train remains on the same parent segment; otherwise, remove old `nT` and build a 
      new one.

> Why not to handle a dynamic list of nodes, where they populated with fixed facilities and pushs/pops as
> are simulated. Each train node is unique and exists only during the train is in the simulation.

4. **Solver integration**
    - `GridModelActor` owns mutations:
        - Build/update the Y-matrix view or its device list just before `solve`.
        - Keep physical devices immutable; maintain a lightweight overlay for virtual nodes and split lines 
          per train.

5. **Multiple trains**
    - Each train has its own `nT`.
    - If two trains land on the same base segment, introduce two distinct virtual nodes and split the segment
      into three parts.

> This should be the base case in point 2 **On-the-fly split (virtual node)**

6. **Performance**
    - Fast mode: apply updates in O(#trains) per tick.
    - Avoid full matrix rebuild when possible—reuse structure and touch only modified rows/cols.

### Data Structures
- `TopologyIndex`
    - `List<Segment>` with `(fromNodeId, toNodeId, length_m, baseResistance)`
    - `findSegment(positionMeters): (segId, f)`
- `VirtualOverlay`
    - `Map<trainId, VirtualNode>` and derived split `Line` views

> In my opinion there is not needs to have separate lists for fixed facilities and trains

### API Changes (actor boundary)
- `UpdateTrainPower(..., positionMeters)` already carries position.
- `GridModelActor`:
    - `bindTrainAt(trainId, positionMeters)` → manages overlay updates.

### Acceptance Criteria
- Node voltages change smoothly as the train moves (no spurious jumps).
- Line loss increases/decreases consistently with split resistances.
- Substation powers reflect changing loading as the coupling point moves.
- CSV shows continuous power evolution; no header changes mid-run.

> Interesting thoughts about titles. In a final solution, we will have a global sheet and dedicated
> sheets för each installation and each train. As trains enter and leave the simulation, it shall 
> be new sheets. 

### Instrumentation
- Optional debug columns: `trainNodeId`, `segmentId`, `f` (0..1).
- Trace logs at INFO when segment changes; DEBUG for sub-tick moves filtered by threshold.

---

## Later Work (backlog)
- `P_lack = P_req - P_trains` column (+ per-train breakdown).
- Replace “Balance” with strict conservation check (or redefine as total including brake power).
- Switch to consistently positive voltage presentation (sign flip at export).
- Expand CSV to multi-sheet Excel (global, per-train, per-substation, lines).
- Unit tests for diode behavior, braking transitions, and topology churn.
- GUI hooks and live plots (optional).

> Improvement of PositionUtils to handle a network.