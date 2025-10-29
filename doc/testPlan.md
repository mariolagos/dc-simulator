# Test Plan (dcSimulator)

This document tracks **developer test cases** used to verify dcSimulator.  
User-facing case studies live in **USER_GUIDE.md**.

_Last updated: 2025-09-22 12:08 UTC_

## 1. Test Taxonomy

| Level | Scope | Typical Target | Notes |
|------|------|-----------------|-------|
| Unit | Function/Class | Utils, stamping helpers | Fast, isolated |
| Component | Module | Solver, GridModel, TrainActor | With fakes/mocks |
| Integration | Multiple modules | Solver + GridModel + IO | End-to-end within process |
| System | Full app | CLI, config, actors | Produces CSV/plots |
| Performance | Throughput/Latency | Solver scaling, I/O | Timed, large configs |
| Regression | Past bugs | Any | Prevents re-introduction |

## 2. Canonical Developer Tests (A1–A5)

| Test ID | Level | Description | Target module | Input | Expected result |
|---------|-------|-------------|----------------|-------|-----------------|
| A1 | Unit | Minimal grid with one node | Solver | `application-test-A1.md` | Stable trivial solution |
| A2 | Component | Substation + line stamping | Solver, GridModel | `application-test-A2.md` | Correct Y-matrix & solution |
| A3 | Component | Train load injection | TrainActor, Solver | `application-test-A3.md` | P_req injected into J-vector |
| A4 | Integration | Multi-train dynamic topology | Dynamic topology | `application-test-A4.md` | Virtual nodes updated correctly |
| A5 | System | End-to-end run | All modules | `application-test-A5.md` | CSV outputs as specified |

## 3. Extended Test Catalog

_Add additional tests here as they are created. Keep rows succinct and reference an input file or doc when possible._

| Test ID | Level | Description | Input | Expected result |
|---------|-------|-------------|-------|-----------------|
| TBD | – | – | – | – |

## 4. Discovered Test Artifacts (Auto-collected)

The following files were **discovered** in the documentation bundle and likely correspond to tests.  
Please map them into Section 3 with proper IDs and expectations.

- (none found)

## 5. How to Add a New Test

1. Create or reference an input file (`application-test-*.md` / `.conf`) in the repo.
2. Add a row under **Section 3. Extended Test Catalog** with a unique **Test ID**.
3. State the **Level**, **Description**, **Input**, and **Expected result**.
4. If the test is canonical and stable, consider promoting it to **Section 2**.
5. Ensure CI (if any) runs the test or validates produced artifacts.
