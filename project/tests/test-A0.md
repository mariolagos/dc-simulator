# Test A0 — Single Substation, Open Circuit (No Loads)

**Goal:** Prove the basic KCL/KVL and sign conventions are correct for the substation stamp.
With a *single* DC substation connected to the bus and *no loads/lines/trains*, the bus shall 
float to the EMF `E` and **no net current** shall flow into the network.

---

## Topology

- Nodes: `0 = ground`, `1 = DC bus`
- Devices:
  - `S1`: Substation from node **1 → 0** (diode towards the bus, i.e., *delivers* to the network when `Va − Vb < E`), internal resistance `Rint`, EMF `E`.
- No lines, no trains.

> **Orientation rule (must hold here):**
> `fromNode = bus`, `toNode = return/ground`.  
> Diode toward bus ⇒ only delivers when `Va − Vb < E` if `allowBackfeed=false`.

---

## Minimal `application.conf`

```hocon
dcsim {
  simulationControl {
    tickDurationSec = 1.0
    simulationStart = "08:00:00"
    simulationEnd   = "08:00:03"
    simulationSpeed = "FAST"
    stopAfterSteps  = 3
  }

  electrics {
    substations {
      defaults { allowBackfeed = false }   # diode behavior (no backfeed)
      # overrides { S1 { allowBackfeed = false } }
    }
  }

  grid {
    groundNodeId = 0

    nodes = [
      { id = 0, position = "1 0+000" },
      { id = 1, position = "1 0+100" }
    ]

    # No lines
    # lines = [ ]

    substations = [
      # S1 feeds node 1 w.r.t. ground
      { id = "S1", nodeId = 1, emf = 1000.0, internalResistance = 0.05 }
    ]
  }

  # No trains / traffic / power profiles
}
```

---

## Expected Results (pass/fail criteria)

Let `E = 1000 V`, `Rint = 0.05 Ω`

With **no loads** attached to node 1, the correct solution is:
- `V(0) = 0 V` (clamped ground)
- `V(1) ≈ E = 1000 V`
- `I[S1] ≈ 0 A`  (no net current into the network)
- `P[S1] ≈ 0 W`  (power delivered to the network = 0)
- `P_lines = 0 W`
- `P_trains = 0 W`
- `P_brake = 0 W`
- **Balance** = `P_substations_out − (P_trains + P_lines)` ≈ `0`
- **Mismatch** ≈ `0`
- **UnderSupply** = `max(0, −Balance)` = `0`
- **UnderReceptivity** = `max(0, Balance)` = `0`

> Tolerance: due to numerics, allow up to `1e-6` relative or `1e-3` absolute volts/amps/watts.

**Plots should show** a flat line at `V(1) ≈ E` and zeros for all powers/currents.

---

## Why this must be true

Norton form of the substation (with Thevenin `E` in series `Rint`) is:
- Parallel conductance `G = 1/Rint` between node 1 and node 0
- Current source of magnitude `I = E/Rint` **from node 0 → node 1**

**Nodal stamping (flow a = node 1, b = node 0):**
- `Y += [[+G, -G], [-G, +G]]`
- `J[a] += +I`, `J[b] += −I`

Solving `G·V = J` with only this device and `V(0)=0` gives `V(1)=E`.  
**Net network current** from S1 is `i_net = G·(E − (V(1) − V(0))) = G·(E − E) = 0`.  
Hence `P_out = (V(1) − V(0)) · i_net = 0`.

---

## Common Failure Modes (what to look for)

- **Symptom:** `I[S1] = E/Rint` (large) and `P[S1] ≈ ±E·E/Rint` while no load present.  
  **Cause:** Wrong J-signs or using `P = I * Va` instead of `P = I * (Va − Vb)`.
- **Symptom:** `V(1) = −E`.  
  **Cause:** Swapped `fromNode`/`toNode`, or reversed J signs.
- **Symptom:** Small non-zero currents/ powers.  
  **Cause:** Missing diode block (should be irrelevant here), or excessive numeric leakage; check your tiny `G_EPS` only.

---

## Instrumentation & Assertions

- Enable the polarity assertion around substations in the solver (diagnostic), but
  **do not fail** this test if the diode condition `Va − Vb ≥ E` (blocked) is observed—
  that is actually **expected** in A0 (still yields `V(1)=E`, `I=0`).
- Log the effective `I_src` stamped for S1 and the solved `V(1)` to confirm `V(1) ≈ E` and `i_net ≈ 0`.
- In CSV, ensure you write:
  - `P[S1] = I_net * (Va − Vb)` (NOT `I * Va`).
  - `I[S1]` is **net** to-network current (positive = delivering to the network).

---

## Expected CSV (example)

```
time,step,V(0),V(1),P[S1],I[S1],P_substations_out,P_trains,P_lines,P_brake,P_req_trains,Balance,Mismatch,UnderSupply,UnderReceptivity
08:00:00,0,0,1000,0,0,0,0,0,0,0,0,0,0,0
08:00:01,1,0,1000,0,0,0,0,0,0,0,0,0,0,0
08:00:02,2,0,1000,0,0,0,0,0,0,0,0,0,0,0
```

---

## If A0 Fails

- Re-check **Substation.stamp** and **DcElectricSolver** substation block:
  - Conductance between (from, to) must be stamped.
  - `J[from] += +I_src`, `J[to] += −I_src` with `I_src = I_lim + G·(Va − Vb)`.
- Verify that `Power = I_net * (Va − Vb)` everywhere.
- Make sure no extra loads/leaks are present in the config.
