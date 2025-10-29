# DC Grid Simulator — Technical Specification (v1)
**Status:** Draft merged spec  
**Scope:** Electrical formulation, sign conventions, device models, solver algorithm, and CSV accounting for the DC traction grid simulator (Akka / Java).  
**Math rendering:** Obsidian / MathJax — inline `$…$`, display `$$ … $$`.

---

## 1) Overview
We model a DC traction grid with:
- **Nodes** (indexed, one is ground $g$).
- **Lines** (pure $R$ between two nodes).
- **Substations** (Thevenin $E$ with series $R_\mathrm{int}$, used as **Norton** between node `from` and node `to` (usually ground)).
- **Trains** (power–dependent two–terminal device between catenary node `from` and return node `to` (usually ground)).

The solver steps a fixed $\Delta t$ and, at each tick, solves a **nodal system** $G \, V = J$ (DC steady state for that instant). All time‑variation (profiles, departures, etc.) is external to the nodal solve.

---

## 2) Topology, Orientation & Notation

### 2.1 Nodes and device orientation
For every two–terminal device we label terminals:
- `$a$` = **from** (catenary / bus side)
- `$b$` = **to** (return / ground side)

The node voltage difference is
$$
\Delta V \equiv V_a - V_b.
$$

> **Substations:** must be connected as `from = DC bus`, `to = return/ground` (i.e., oriented **toward** the DC network).  
> **Lines:** orientation is arbitrary; formulas use $V_a-V_b$.  
> **Trains:** `from = DC bus` to `to = return/ground`.

### 2.2 Sign conventions (currents & powers)

- **Device current direction:** $I>0$ means **current flows from** `$a$` **to** `$b$` through the device.

- **Nodal‑source sign ($J$ vector):** $J_k$ is **current injected** **into** node $k$ by independent/current‑source terms.  
  - A current source **from `$a$` to `$b$`** contributes:  
    $$J_a \mathrel{+}= +I,\qquad J_b \mathrel{+}= -I.$$
  - A current sink (drawing current) behaves with the **opposite injection**.

- **Device power (network view):**
  $$
  P_\text{dev} \equiv I \cdot \Delta V.
  $$
  Interpreted as power **delivered by the device to the network** if $P_\text{dev}>0$.  
  — For **substation** this is **source power** (to the network).  
  — For **train**, we also record **requested** power and split **braking** into network vs. brake resistor.

- **Line loss:** always non‑negative
  $$
  P_\text{loss} = I^2 R \ge 0.
  $$

---

## 3) Device Models

### 3.1 Line (pure R)

- Ohm’s law:
  $$I_{ab} = \frac{V_a - V_b}{R}.$$
- Stamp (conductance branch):
  $$
  g \equiv \frac{1}{R},\qquad
  \begin{bmatrix}
  +g & -g\\
  -g & +g
  \end{bmatrix}
  \text{ added to } G \text{ at } (a,b).
  $$
- Loss:
  $$P_\text{loss}=I_{ab}^2\,R.$$

### 3.2 Substation (Thevenin → Norton) with diode/backfeed and optional current limit

- Parameters: EMF $E$ (V), internal resistance $R_\mathrm{int}$ ($g=1/R_\mathrm{int}$), flags: `allowBackfeed` (diode open/closed), optional `maxCurrentA`.

- **Net Norton current** (ideal, before limiting) for the present iterate:
  $$
  I_\text{raw} = g \, (E - \Delta V).
  $$

- **Diode/backfeed rule:**  
  - If `allowBackfeed = false` (diode **toward** the DC network): **block** when $\Delta V \ge E$ (no delivery).  
    $$I_\text{raw} \leftarrow \max(0,\; I_\text{raw}).$$
  - If `allowBackfeed = true`: no blocking; can **absorb** or **deliver**.

- **Current limit (optional):**
  $$
  I_\text{lim} = \operatorname{sgn}(I_\text{raw}) \cdot \min\!\big(|I_\text{raw}|,\, I_\max\big).
  $$

- **Stamping** (always add the **conductance branch**, except when explicitly blocked you may use a tiny $g_\text{leak}$ for numerical stability):
  $$
  \text{add } 
  \begin{bmatrix}
  +g & -g\\
  -g & +g
  \end{bmatrix}
  \text{ to } G.
  $$

- **Effective source injection** to realize $I_\text{lim}$ at the current iterate ($\Delta V$):
  $$
  I_\text{src} \;=\; I_\text{lim} \;+\; g \,\Delta V.
  $$
  **Nodal injections (source from $a\to b$):**
  $$
  J_a \mathrel{+}= +I_\text{src},\qquad J_b \mathrel{+}= -I_\text{src}.
  $$

- **Power accounting (network view):**
  $$
  P_\text{sub} = I_\text{lim}\, \Delta V.
  $$
  **Internal loss:**
  $$
  P_\text{int} = (E - \Delta V)\,I_\text{lim} = g\,(E-\Delta V)^2 \ge 0 \quad \text{when delivering}.
  $$

> **Polarity sanity:** In normal motoring, at active stations $\Delta V < E$; when $\Delta V \ge E$ a diode station blocks.

### 3.3 Train (power‑dependent current sink with regen split)

Let the requested powers (W) per tick be:
- Motoring $P_\text{mot}\ge 0$,
- Braking $P_\text{brk}\le 0$ (regen sign is **negative**),
- Auxiliaries $P_\text{aux}\ge 0$.

Total requested:
$$
P_\text{req} = P_\text{mot} + P_\text{brk} + P_\text{aux}.
$$

To avoid singular behavior near $\Delta V\approx 0$, define
$$
\Delta V_\text{eff} = 
\begin{cases}
\operatorname{sgn}(\Delta V)\, V_\text{FLOOR}, & |\Delta V| < V_\text{FLOOR} \\[2px]
\Delta V, & \text{otherwise}
\end{cases}
$$
with $V_\text{FLOOR}\approx 50\,\mathrm{V}$.

**Regen line fraction** (diode receiving capability) using cutoff and max substation voltage limits:
$$
\phi_\text{line} =
\begin{cases}
1, & |\Delta V| \le V_\text{cut}\\
0, & |\Delta V| \ge V_\text{max}\\
\dfrac{V_\text{max} - |\Delta V|}{V_\text{max} - V_\text{cut}}, & \text{otherwise}
\end{cases}
$$

Then the **device current** (load sign: draws from network in motoring) is
$$
I_{ab}=
\begin{cases}
\dfrac{P_\text{req}}{\Delta V_\text{eff}}, & P_\text{req}\ge 0 \quad\text{(motoring)}\\[8px]
\dfrac{P_\text{brk}\,\phi_\text{line}}{\Delta V_\text{eff}}, & P_\text{req}<0 \quad\text{(braking; } P_\text{brk}\le 0\text{)}
\end{cases}
$$
with optional current limit $|I_{ab}|\le I_\text{max}$.

**Stamping (current sink):**
$$
J_a \mathrel{+}= -I_{ab},\qquad J_b \mathrel{+}= +I_{ab}.
$$

**Power accounting (network view & brake resistor):**
$$
\begin{aligned}
P_\text{line}  &= I_{ab}\, \Delta V,\\
P_\text{brake} &= (-P_\text{brk})\,(1-\phi_\text{line}) \;\ge\; 0,\\
P_\text{req}   &= P_\text{mot} + P_\text{brk} + P_\text{aux}.
\end{aligned}
$$

---

## 4) Solver Algorithm (per tick)

1. **Seed voltages:** $V_g=0$, others small; seed bus nodes near $E$ for faster convergence.
2. **Build $G$ and $J$:**
   - Lines: add conductance branch.
   - Substations: compute $I_\text{src}$ at current iterate; add branch and injections; if diode‑blocked, use tiny $g_\text{leak}$ and no source injection.
   - Trains: compute $I_{ab}$ from requested powers and $\Delta V_\text{eff}$; add injections.
3. **Numeric anchor:** add tiny shunt $g_\epsilon$ to each non‑ground diagonal to avoid singular islands.
4. **Clamp ground:** set ground row to unity; $V_g=0$.
5. **Solve:** $V \leftarrow G^{-1}J$ (LU). Repeat until $\|V^{(k+1)}-V^{(k)}\|<\varepsilon$ or max iters.

**Default numerics:**  
$\varepsilon=10^{-9}$, $V_\text{FLOOR}=50\ \mathrm{V}$, $g_\epsilon=10^{-9}\ \mathrm{S}$, EPS$=10^{-6}$.

---

## 5) CSV Accounting & Totals

Per tick we write:
- **Node voltages**: $V_n$.
- **Lines**: $I_{ab}$ and $P_\text{loss}$.
- **Substations**: $I_\text{sub}$ and $P_\text{sub} = I_\text{lim}\Delta V$ (network‑delivered power). Optionally $P_\text{int}$.
- **Trains**: $P_\text{line}$ (signed), $P_\text{req}$ (signed), $P_\text{brake}$ (non‑neg).

**Totals (diagnostics):**
$$
\begin{aligned}
P_\text{sub}^{\Sigma}   &= \sum_\text{sub} P_\text{sub},\\
P_\text{loss}^{\Sigma}  &= \sum_\text{lines} I^2R,\\
P_\text{train}^{\Sigma} &= \sum_\text{trains} P_\text{line},\\
P_\text{brake}^{\Sigma} &= \sum_\text{trains} P_\text{brake},\\
P_\text{req}^{\Sigma}   &= \sum_\text{trains} P_\text{req}.
\end{aligned}
$$

**Power balance (should be $\approx 0$ within numerical tolerance):**
$$
\boxed{\;\; \text{mismatch} \;=\; P_\text{sub}^{\Sigma} \;-\; P_\text{loss}^{\Sigma} \;-\; P_\text{train}^{\Sigma} \;\;}
$$

**Under‑receptivity (regen not accepted by network):**
$$
\boxed{\;\; U_\text{recept} \;=\; P_\text{brake}^{\Sigma} \;\ge\; 0 \;\;}
$$

**Under‑supply (motoring request not met by the network; proxy):**
Let $P_\text{demand}^\Sigma = \sum \max(0,\; P_\text{req})$ (mot+aux only). A simple diagnostic is
$$
\boxed{\;\; U_\text{supply} \;\approx\; \max\big(0,\; P_\text{demand}^\Sigma - P_\text{train}^{\Sigma}\big)\;\;}
$$
This is a coarse metric; a more exact estimator would account for per‑train caps.

> **Note:** *Do not* put line losses into the mismatch twice. The balance above already subtracts $P_\text{loss}^{\Sigma}$.

---

## 6) Polarity & Sanity Checks

At runtime (solver iteration), you may assert:
- **Station forward/blocked:** if `allowBackfeed=false` and station is stamped as forward, require $\Delta V < E - \text{EPS}$. If violated, either block or throw (debug mode).
- **Injection orientation:**
  - Substation (source a→b): `J[a]+=+I_src; J[b]+=-I_src`.
  - Train (sink a→b): `J[a]+=-I; J[b]+=+I`.
- **Power sign:** $P_\text{sub}=I\Delta V$ is **delivered** to network; $P_\text{line}=I\Delta V$ for trains is **drawn** from network when positive.
- **No double accounting:** Only lines contribute $I^2R$ losses; substation power is *before* losses; train power is what the network sees at the train terminals.

---

## 7) Approximations & Defaults
- **$\Delta V_\text{eff}$ floor** to keep $I=P/\Delta V$ bounded near zero.
- **Regen ramp** between $V_\text{cut}$ and $V_\text{max}$ is linear; real systems may deviate.
- **Tiny diagonal shunts** to eliminate singularities on floating islands.
- **Per‑device $I_\text{max}$ limit** is a hard clamp.

**To‑Do:**  
- Spline interpolation of power profiles.  
- Move train anchor with position.  
- More detailed substation receptivity model and feeder saturation.  
- Separate computation vs. I/O: keep CSV writer free of “business logic”.

---

## 8) Code Mapping (where things live)

- **`DcElectricSolver`**
  - Builds $G$ and $J$ (lines, substations, trains).
  - Applies diode/backfeed + current limit for substations via effective source $I_\text{src}$.
  - Trains stamped as current sinks based on $P_\text{req}$ and $\Delta V_\text{eff}$.
  - Solves, returns `GridResult` with per‑node $V$, per‑device $I$, $P$, and $P_\text{req}`/`P_\text{brake`.

- **`Substation`**
  - `stamp(...)` mirrors the solver’s stamping (conductance branch + effective source injection).
  - `computeCurrent/Power/InternalLoss(...)` use the same sign conventions.

- **`GridModelActor`**
  - Orchestrates ticks, calls solver, populates missing values if needed, writes CSV.  
  - Any re‑computation must follow the **same** formulas.

- **`ResultCsvWriter`**
  - Pure I/O. Uses values already computed in `GridResult` to write per‑device and totals.  
  - Balance columns use the equations in §5.

---

## 9) Troubleshooting Checklist

1. **Station power too large:**  
   - Verify $P_\text{sub}=I\Delta V$, **not** $I\cdot V_a$.  
   - Ensure `J[a]+=+I_src; J[b]+=-I_src` and `from=bus`, `to=ground`.
2. **Negative node voltages or plateau:** ground node mis‑set or double stamping; check tiny shunts and ground clamping.
3. **Mismatch ≈ line losses during braking:** ensure line losses are included **once** in balance (see §5).
4. **Regen not showing under‑receptivity:** confirm $P_\text{brake}$ is recorded and summed.
5. **Hacky curves near $\Delta V \approx 0$:** raise $V_\text{FLOOR}$ slightly or smooth requests; later switch to spline profiles.

---

## 10) Constants (defaults)
- `SOLVE_TOL = 1e-9`  
- `V_DIFFF_FLOOR = 50 V`  
- `G_EPS = 1e-9 S`  
- `EPS = 1e-6 V`

---

## 11) Glossary
- **Injected current ($J$):** current entering the node from independent/current‑source elements.
- **Delivered power:** positive when device delivers power to **the network**.
- **Under‑receptivity:** regen power rejected by the network, burned in the train’s brake resistor.
- **Under‑supply:** motoring demand not met at the network terminals (diagnostic proxy).

