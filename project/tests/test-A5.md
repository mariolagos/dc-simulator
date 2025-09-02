# Test A5 — Regeneration Above Cutoff (Under-Receptive → Brake Resistor)

**Purpose:** When |ΔV| ≥ vmax (or well above cutoff), the diode prevents line regen; braking should be 
dumped into the **brake resistor**. This validates `pBrake` path and the under-receptivity accounting.

## Topology
- Nodes: 0=ground, 1=bus, 2=train
- Substation: S1 at node 1 (E=1000 V, Rint=0.05 Ω), **allowBackfeed=false**
- Line: 1–2 (R=0.10 Ω)
- Train: T1 at 2→0, cutoff=850, vmax=1000

## Train profile
- Constant braking request: **-500 kW**

## Expected
- `P_train` on the **line** near **0** (since regen is blocked)
- `P_brake ≈ 500 kW`
- `P_substations_out ≈ 0` or positive only for keep-alive leakage
- `UnderReceptivity ≈ P_brake`
- `Balance ≈ 0`

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
      defaults { allowBackfeed = false }
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
    templates  = { T1 = { stops = [ { signature = "X", departure = "08:00:00" } ] } }
  }

  powerProfiles { motoringAndAuxiliariesInSameModel = false, auxiliaryPower = 0.0, templates = [ { id = "T1", folder = "inline", legs = [] } ] }
}
```
