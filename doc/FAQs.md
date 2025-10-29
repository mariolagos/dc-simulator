# DcSim — Frequently Asked Questions (FAQs)

_Last updated: 2025-09-11_

---

## 1) What does `export.treatNegativeBrakingAsBrake` do?

**Short answer:** It’s **reporting-only**.
- **`false` (recommended):** Regen (negative traction) flows in the network and is **not** mirrored as `P_brake`.
- **`true`:** Any negative traction (regen) is **also** shown as a positive `P_brake` number in the CSV, even though power still goes back into the network.

**When to use `true`:**
- Quick sanity plots when you don’t have a dedicated dissipative brake model but still want to visualize “brake-like” energy.
- Post-processing where you want a single “brake-looking” time series.

**Caveat:** With `true`, `Balance` will typically **not** be zero during regen periods, because you’re double-counting energy (once in the network, once as “brake”).

---

## 2) Do we still need the anchor node (e.g., node `99`)?

**It depends on how the train is modeled:**
- **Anchor component in use (`TrainAnchorComponent`):** Yes, keep a dedicated “loose” anchor node so the component can inject/measure at a fixed point.
- **Profile-driven trains only (no anchor component):** You don’t strictly need it. If you keep it around for convenience, set:
    - `train.pW = 0.0` so the anchor contributes **no constant CP load**.
    - `train.vMS` may still be used for kinematics-only display, but not for power.

Future refactors can remove the explicit anchor node for profile-only scenarios.

---

## 3) Are these charts expected for the provided config?

Given your config (3 substations, `allowBackfeed=false`, Excel power profile, anchor CP set to `pW=0.0`):

- **`UnderSupply` ≈ 0:** Substations are strong enough; no shortage relative to `P_req`.
- **`UnderReceptivity` > 0 only during regen:** Because `allowBackfeed=false`, substations do not absorb energy. If the network (including other trains/loads) can’t accept the returning power, that shortfall shows as `UnderReceptivity`.
- **`Balance` vs `Mismatch`:**
    - `Mismatch = P_substations_out − P_trains − P_lines`  
      (numerically small if the model is well-posed; may show residuals due to discretization and device reporting granularity)
    - `Balance = Mismatch − P_brake`  
      With pure regen (no dissipative brake), `P_brake ≈ 0`, so `Balance ≈ Mismatch`.  
      If you enable the reporting trick (`treatNegativeBrakingAsBrake=true`), `Balance` will deviate during regen.

These behaviors match what you’re seeing.

---

## 4) How do I send **dissipative brake** vs **regen** from `TrainActor`?

Use the `UpdateTrainPower` contract:

- `motoringKW` **≥ 0**
- `brakingKW` **≤ 0**  → **regen** back into the network
- `brakingKW` **> 0**   → **dissipative brake** (heat off-network)

**Example mapping from a net power `P_net` (W):**
```java
double kW = P_net / 1000.0;
double motoringKW = Math.max(0.0, kW);   // load from network
double brakingKW  = Math.min(0.0, kW);   // regen to network (negative here)

// If you want a physical resistor brake instead of regen at some moment:
double dissipativeKW = 500.0;            // e.g., 500 kW dumped to resistor
motoringKW = 0.0;
brakingKW  = +dissipativeKW;             // POSITIVE => dissipative brake
