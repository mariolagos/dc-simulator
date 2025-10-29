# DcSim – Developer Backlog

This document tracks planned and active change requests (CRs), technical tasks, and improvements.

---

## Legend
| Status | Meaning |
|---------|----------|
| ߟ Open | Defined and planned, not yet implemented |
| ߟ In Progress | Implementation ongoing |
| ߔ Review | Under testing or review |
| ⚪ Done | Completed and merged |

---

## ߔ Change Requests (CR)

| ID | Title | Status | Description | Owner |
|----|--------|---------|-------------|--------|
| **CR01** | Test Platform Refactor | ߟ Open | Clean up and stabilize the test platform. Remove legacy helpers, reflection paths, and unify test naming. | TBD |
| **CR02** | Documentation Alignment | ߟ Open | Update README_dev and CONTRIBUTING with current branch/test policy. Clarify Unit/Integration/Param test structure. | TBD |
| **CR03** | Chaining (time-stepped simulation pipeline) | ߟ Open | Deterministic chaining of time steps using previous solution as initial state; stable stamping order; resumable runs; robust Excel export. | TBD |

---

## ߔ Technical Tasks

| ID | Title | Status | Description | Owner |
|----|--------|---------|-------------|--------|
| **T01** | CI Smoke Job | ߟ Open | Add a lightweight smoke test job for core tests (`MiniMiniSolverTests`, etc.). |
| **T02** | Disable Verbose by Default | ⚪ Done | Turn off default verbosity, keep opt-in via `-Ddcsim.verbose=true`. |
| **T03** | Static Analysis | ߟ Open | Enable `-Xlint:unchecked,deprecation` and baseline coverage using JaCoCo. |

---

## ߔ Future / Nice-to-Have

| Title | Description |
|--------|-------------|
| Closed-Loop Train Controller (flagged) | Controller-in-loop (vmin throttle, vmax clamp), enabled via `-Ddcsim.closedLoop=true`. |
| Multi-train actor scheduling | Introduce event-driven Akka logic for multi-train scenarios. |
| Dynamic topology simulation | Handle runtime switching and fault states via Power Simulator. |
| DB telemetry backend | Store signals/samples in SQL/embedded DB instead of Excel/CSV. |

---

_Last updated: 2025-10-14_
