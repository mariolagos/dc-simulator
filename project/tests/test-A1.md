# Test A1 — Open Circuit (Single Substation, No Loads)

**Purpose:** Verify Norton/diode model and stamp polarity. With no external load, the bus should float
to **V ≈ E** and **net current = 0** (hence **P[S]=0**). This catches any sign errors that would otherwise 
force phantom current.

## Topology
- Nodes: 0=ground, 1=bus
- Devices: One substation S1 from node **1 → 0** (bus → return), **allowBackfeed=false**
- No lines, no trains

## Expected
- V(0)=0 V
- V(1)≈E (1000 V)
- I[S1]≈0 A, P[S1]≈0 W
- Totals: `P_substations_out ≈ 0`, `P_trains = 0`, `P_lines = 0`, `Mismatch ≈ 0`

## application.conf
```hocon
dcsim {
  simulationControl {
    tickDurationSec = 1.0
    simulationStart = "08:00:00"
    simulationEnd   = "08:00:10"
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
      { id = 1, position = "L 0+100" }
    ]
    # No lines
    substations = [
      { id = "S1", nodeId = 1, emf = 1000.0, internalResistance = 0.05 }
    ]
  }

  # No trains / traffic
  trains { defaults { cutoffVoltage = 850.0, maxVoltage = 1000.0, maxCurrentA = 4000.0 } }
  traffic { timetable { trains = [] } }
  powerProfiles { templates = [] }
}
```
