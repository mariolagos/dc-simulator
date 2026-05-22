# Test A3 — Motoring Train (One Substation)

**Purpose:** Validate motoring power flow. Substation should **deliver** power; `P_lines > 0` (losses); train consumes (`P_train > 0`); **Balance ≈ 0**.

## Topology
- Nodes: 0=ground, 1=substation bus, 2=train node
- Substation: S1 at node 1 (E=1000 V, Rint=0.05 Ω), allowBackfeed=false
- Lines: 1–2 (R=0.10 Ω)
- Train: T1 anchored 2→0

## Train profile
- Constant motoring request: **1.0 MW**
- Aux = 0

## Expected (order-of-magnitude)
- V(1) slightly below 1000 V due to Rint drop; V(2) below V(1)
- I[S1] ~ 1000 A, `P_lines` tens of kW (depends on voltages)
- `Balance = P_substations_out - P_trains - P_lines ≈ 0`
- No braking: `P_brake = 0`; `UnderReceptivity = 0`

## application.conf
```hocon
dcsim {
  simulationControl {
    tickDurationSec = 1.0
    simulationStart = "08:00:00"
    simulationEnd   = "08:00:30"
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

  trains {
    defaults { cutoffVoltage = 850.0, maxVoltage = 1000.0, maxCurrentA = 4000.0 }
  }

  traffic {
    timetable {
      trains = [
        { id = "T1", templateId = "T1", departure = "08:00:00", headway = "00:00:00", count = 1, signature = "X" }
      ]
    }
    templates = {
      T1 = {
        stops = [
          { signature = "X", departure = "08:00:00" }
        ]
      }
    }
  }

  powerProfiles {
    motoringAndAuxiliariesInSameModel = false
    auxiliaryPower = 0.0
    templates = [
      { id = "T1", folder = "inline", legs = [] }
    ]
  }
}
```
**Note:** Use your `TrainActor` to issue a flat 1000 kW motoring request for T1.
