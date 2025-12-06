# DC-Simulator – Prototype Test Report (v0.6)
Date: 2025-12-01  
Status: Prototype verified (3S1T baseline + 3S2T regen)

This document summarizes the verification of the DC-Simulator’s braking, motoring and regenerative braking behaviour using scenario-based tests.  
All results are based on longtable output, pivoted to wide-format Excel files.

Test evidence files and scenario configuration files are included as appendices.

---

## 1. Purpose
The purpose of this test report is to document the correct electrical behaviour of the prototype DC-simulator under:

- Motoring (traction power)
- Resistive braking
- Regenerative braking
- Mixed braking/motoring (receptivity phases)
- Multi-substation power distribution

The tests verify that the simulator correctly represents:

- Power flows (sign, magnitude, split)
- Substation behaviour (P_net, P_loss, V)
- Train voltage evolution
- Receptivity logic (no/partial/full regen)
- Energy consistency

---

## 2. Test Scenarios
Two scenarios were evaluated.

### 2.1 3S1T – Three Substations, One Train
Purpose:  
Baseline scenario with no regenerative braking.  
Train brakes resistively.  
Validates fundamental solver behaviour.

Files:
- 3S1T_wide.xlsx
- 3S1T_application.conf

### 2.2 3S2T – Three Substations, Two Trains
Purpose:  
Validate regenerative braking with two trains sharing the network.  
Three receptivity regimes appear:
- No receptivity (regen → resistor only)
- Partial receptivity (regen split between motoring train and resistor)
- Full receptivity (all regen consumed by motoring train; remainder supplied by substations)

Files:
- 3S2T_wide.xlsx
- 3S2T_application.conf

---

## 3. Summary of Results
### 3S1T Result: PASSED
- Motoring produces consistent voltage drop and positive P_net_W.
- Resistive braking produces:
    - P_brk_req_W < 0
    - P_brk_net_W = 0
    - P_brk_res_W = P_brk_req_W
- Substations show no negative P_net_W (correct: no regen).
- Train voltage remains within expected range (approx. 830–900 V).
- Substation losses and currents are consistent.

### 3S2T Result: PASSED
All three receptivity regimes observed:

1. **No receptivity**
    - Train1 brakes, Train2 not motoring
    - All braking energy → resistor
    - All substations have P_net_W ≥ 0

2. **Partial receptivity**
    - Train1 brakes, Train2 motoring moderately
    - Regen split: part to Train2 (P_brk_net_W < 0), part to resistor
    - Some substations show P_net_W < 0 (regen absorption)

3. **Full receptivity**
    - Train1 brakes lightly
    - Train2 absorbs all regen
    - P_brk_res_W = 0
    - Substations supply remaining traction deficit

Train voltages match substation voltages with correct IR behaviour. Energy flows are physically consistent.

---

## 4. Detailed Observations

### 4.1 Scenario 3S1T

#### 4.1.1 Motoring (t = 20 s)
Train:
- mot_W = 2 000 000 W
- P_net_W = 1 900 000 W
- V_V ≈ 831.2 V
- speed ≈ 72 m/s

Substations:
- SS0.P_net_W = 572 kW
- SS1.P_net_W = 799 kW
- SS2.P_net_W = 572 kW

Conclusion:  
Correct power distribution and voltage drop.

#### 4.1.2 Resistive braking (t = 85 s)
Train:
- P_brk_req_W = -2 000 000 W
- P_brk_net_W = 0
- P_brk_res_W = -2 000 000 W
- P_net_W = 0
- V_V = 900 V

Substations:
- All P_net_W = 0
- All V_V = 900 V

Conclusion:  
All brake energy is dissipated on board.  
No regenerative behaviour (correct for 3S1T).

---

### 4.2 Scenario 3S2T

#### 4.2.1 No Receptivity (t = 47 s)
Train1:
- P_brk_req_W = -2 300 000
- P_brk_net_W = 0
- P_brk_res_W = -2 300 000

Train2:
- Not motoring

Substations:
- All P_net_W ≈ 0
- All V_V = 900 V

Conclusion:  
Regen is impossible → resistor absorbs all energy.

---

#### 4.2.2 Partial Receptivity (t = 58 s)
Train1:
- P_brk_req_W = -1 200 000
- P_brk_net_W = -700 000
- P_brk_res_W = -500 000

Train2:
- mot_W = +800 000
- P_net_W = +700 000

Substations:
- SS0.P_net_W = –58 kW
- SS1.P_net_W = –84 kW
- SS2.P_net_W = +161 kW

Conclusion:  
Correct splitting of braking power.  
Both Train2 and substations absorb regenerated energy.

---

#### 4.2.3 Full Receptivity (t = 65 s)
Train1:
- P_brk_req_W = –500 000
- P_brk_net_W = –500 000
- P_brk_res_W = 0

Train2:
- mot_W = +1 500 000
- P_net_W = +1 400 000

Substations:
- All P_net_W positive (supply remaining demand)

Conclusion:  
Full receptivity: all regen is absorbed by Train2.

---

## 5. Global Consistency Checks
- Voltage behaviour always matches load conditions.
- Currents consistent with power (P = V · I).
- Substation losses follow (E − V)² / R_int trend.
- No illegal sign reversals.
- Energy balance holds qualitatively.

---

## 6. Conclusion
The prototype DC-simulator behaves correctly under both baseline (3S1T) and regenerative (3S2T) scenarios.  
Receptivity logic works across all regimes.  
Substation and train voltage/power behaviour is consistent and physically meaningful.

This version is suitable for external presentation and for progression to MATLAB-integration testing.

---

## Appendix A – [test_evidence_v0.6.zip](test_evidence_v0.6.zip)
- 3S1T_wide.xlsx
- 3S2T_wide.xlsx
- 3S1T_application.conf
- 3S2T_application.conf

