# Technical Description (Draft)

## Governing Equations

We model the DC railway traction network as a resistive network with current sources and nonlinear loads.

The core equation is the nodal form of **Kirchhoff’s current law (KCL)**:

$$
Y \cdot V = J
$$

where:
- $Y$ is the nodal admittance matrix,
- $V$ is the vector of node voltages,
- $J$ is the current injection vector.

---

## Substation Model

A substation is modeled as an ideal EMF $E$ in series with an internal resistance $R$.

This can be written as a Norton equivalent:

$$
I = G \cdot (E - V)
$$

where:
- $G = \frac{1}{R}$ is the conductance,
- $V$ is the node voltage at the feeding point.

The corresponding **power balance** is:

- Output power to the network:

$$
P_{\text{out}} = V \cdot I
$$

- Internal loss:

$$
P_{\text{loss}} = (E - V) \cdot I = \frac{I^2}{G}
$$

---

## Train Load Model

A train requests a power $P_{\text{req}}$ at the catenary voltage $V$.  
The corresponding current is computed as:

$$
I = \frac{P_{\text{req}}}{V}
$$

At low voltages this naturally increases the current demand, but a nonlinear saturation or protection may be added:

$$
I = \min\!\left(\frac{P_{\text{req}}}{V}, I_{\text{max}}\right)
$$

---

## Iterative Solver

1. Initialize voltages with ground fixed: $V_{\text{ground}} = 0$.
2. At each iteration:
   - Clear $Y$ and $J$.
   - Stamp all devices (substations, lines, trains).
   - Enforce ground constraint.
   - Solve $Y \cdot V = J$.
3. Check convergence:

$$
\Delta = \| V^{(k+1)} - V^{(k)} \|
$$

Stop if $\Delta < \epsilon$.

---

## Power Balance Check

At each timestep, we check:

$$
\sum P_{\text{substations, out}} \;=\; \sum P_{\text{trains}} \;+\; \sum P_{\text{lines}} \;+\; \sum P_{\text{losses}}
$$

Any imbalance indicates a modeling or numerical error.

---
