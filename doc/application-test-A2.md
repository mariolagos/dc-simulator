# Test A2 — Two Substations, Equal EMF, Interconnected Buses (No Trains)

**Purpose:** With **equal EMF** and identical internal resistances, two diode substations interconnected by lines should settle at **V ≈ E** on both buses, with **~0 line current** and **P[S] ≈ 0**. This guards against double-injection or mistaken backfeed.

## Topology
- Nodes: 0=ground, 1=bus A, 2=mid, 3=bus B
- Stations: S1 at node 1, S2 at node 3, both E=1000 V, Rint=0.05 Ω, allowBackfeed=false
- Lines: 1–2 and 2–3 (each R=0.10 Ω) for an inter-bus path
- No trains

## Expected
- V(1)≈V(3)≈1000 V, V(2)≈1000 V, V(0)=0 V
- I[S1]≈I[S2]≈0 A; P[S1]≈P[S2]≈0 W
- I[L_1_2]≈I[L_2_3]≈0 A; P_lines≈0
- Totals: Mismatch≈0

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
      { id = 1, position = "L 0+100" },
      { id = 2, position = "L 0+600" },
      { id = 3, position = "L 1+100" }
    ]
    lines = [
      { from = 1, to = 2, resistance = 0.10, category = "u" },
      { from = 2, to = 3, resistance = 0.10, category = "u" }
    ]
    substations = [
      { id = "S1", nodeId = 1, emf = 1000.0, internalResistance = 0.05 },
      { id = "S2", nodeId = 3, emf = 1000.0, internalResistance = 0.05 }
    ]
  }

  trains { defaults { cutoffVoltage = 850.0, maxVoltage = 1000.0, maxCurrentA = 4000.0 } }
  traffic { timetable { trains = [] } }
  powerProfiles { templates = [] }
}
```
