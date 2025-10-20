## DcSim - application.conf Template
```
dcsim {
simulationControl {
# Core simulation timing
dtSec  = 0.1
steps  = 1500

    # CSV sampling — single source of truth
    csvEveryNthStep = 1
}

export {
# Reporting behavior (no physics is fabricated in the writer):
#
# false (recommended now): do NOT mirror negative traction (regen) as "P_brake".
# true: purely a reporting trick — shows P_brake even though regen still flows in the network.
#       This makes Balance != 0 whenever regen exists.
treatNegativeBrakingAsBrake = false
}


trains {
# Electrical limits used when TrainLoad devices are created implicitly.
defaults {
cutoffVoltage = 900.0
maxVoltage    = 1000.0
maxCurrentA   = 4000.0
}

    # Optional per-train overrides
    overrides {
      # Example:
      # T1 { maxCurrentA = 5000.0 }
    }
}

powerProfiles {
# Choose exactly ONE style per train:

    # (A) Simple constant power (recommended for quick tests)
    templates = [
      {
        id = "T1"
        constantKW = 2500.0      # sign convention: +motoring, -regeneration
        duration   = "00:02:30"
      }
      # You can add more trains like:
      # { id = "T2", constantKW = -1500.0, duration = "90" }  # 90 seconds regen
    ]

    # (B) Legacy Excel reader (if you still use sheets) — comment (A) out if you enable this.
    # templates = [
    #   {
    #     id     = "T1"
    #     folder = "input/loads/T1"
    #     legs = [
    #       { file = "A-B.xlsx" },
    #       { file = "B-C.xlsx" }
    #     ]
    #   }
    # ]
}
}
```
**Sign conventions (as implemented now)**

<ul>
<li>TrainActor sends</li>
<li>
motoringKW = max(0, tractionKW)</li>
<li>brakingKW = min(0, tractionKW) (negative = regen to the network; positive would 
mean dissipative brake request)</li>
<li>Writer computes:</li>
<li>P_substations_out = Σ V_bus * I_substation</li>
<li>P_lines = Σ P[line]</li>
<li>P_trains = Σ P[train] (>0 consumption, <0 regen)</li>
<li>P_substations_internal = Σ (I_sub² * R_internal) (not equal to P_substations_out)</li>
<li>Mismatch = P_substations_out − P_trains − P_lines − P_substations_internal</li>
<li>Balance = Mismatch − P_brake (where P_brake is reporting-only, fed via 
setLatestBrakeW)</li>
<li>With treatNegativeBrakingAsBrake = false, both Mismatch and Balance should be ≈ 0 
(numerics aside) when topology/params are sane.</li>
</ul>

