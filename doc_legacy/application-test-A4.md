# Test A4 — Regeneration Below Cutoff (Receptive Network)

**Purpose:** When |ΔV| < cutoff, regen should be allowed onto the line. With a **receptive substation** (`allowBackfeed=true`) or other loads, train braking power should flow into the network; `P_brake ≈ 0` if fully receptive.

## Topology
- Nodes: 0=ground, 1=bus, 2=train node
- Substation: S1 at node 1 (E=1000 V, Rint=0.05 Ω), **allowBackfeed=true**
- Line: 1–2 (R=0.10 Ω)
- Train: T1 at 2→0, cutoff=850, vmax=1000

## Train profile
- Constant braking request: **-500 kW** (regen)

## Expected
- `P_train ≈ -500 kW` (negative means export)
- `P_substations_out` may become **negative** (absorbing power) or near-zero depending on Va
- `P_brake ≈ 0`
- `Balance ≈ 0` (substations + trains + lines)

## application.conf
```hocon
dcsim {
  simulationControl {
    tickDurationSec = 1.0
    simulationStart = "08:00:00"
    simulationEnd   = "08:00:20"
    simulationSpeed = "FAST"
  }

  electrics {
    substations {
      defaults { allowBackfeed = true }
      overrides { }
    }
  }

  grid {
    groundNodeId = 0
    nodes = [
      { id = 0, position = "L 0+000" },
      { id = 1, position = "L 0+100" },
      { id = 2, position = "L 0+600" }
    ]
    lines = [
      { from = 1, to = 2, resistance = 0.10, category = "u" }
    ]
    substations = [
      { id = "S1", nodeId = 1, emf = 1000.0, internalResistance = 0.05 }
    ]
  }

  trains { defaults { cutoffVoltage = 850.0, maxVoltage = 1000.0, maxCurrentA = 4000.0 } }

  traffic {
    timetable { trains = [ { id = "T1", templateId = "T1", departure = "08:00:00", headway = "00:00:00", count = 1, signature = "X" } ] }
    templates = { T1 = { stops = [ { signature = "X", departure = "08:00:00" } ] } }
  }

  powerProfiles { motoringAndAuxiliariesInSameModel = false, auxiliaryPower = 0.0, templates = [ { id = "T1", folder = "inline", legs = [] } ] }
}
```
