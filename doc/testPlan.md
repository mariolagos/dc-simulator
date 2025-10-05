# Test Plan (dcSimulator)

This document describes the **developer test cases (A1–A5)** used to verify components of dcSimulator.  
These are **not user-facing** examples (see USER_GUIDE.md for case studies).

## Test Case Table

| Test ID | Description | Target module | Input | Expected result |
|---------|-------------|----------------|-------|-----------------|
| A1 | Minimal grid with one node | Solver | `application-test-A1.md` | Solver returns stable trivial solution |
| A2 | Small grid with substation and line | Solver, GridModel | `application-test-A2.md` | Correct Y-matrix stamping and solution |
| A3 | Train load injection test | TrainActor, Solver | `application-test-A3.md` | Power request correctly injected into J-vector |
| A4 | Multi-train dynamic topology | Dynamic topology handler | `application-test-A4.md` | Virtual nodes created and removed correctly |
| A5 | End-to-end integration test | All modules | `application-test-A5.md` | Simulation runs end-to-end with expected CSV outputs |

---

## Notes

- Test cases A1–A5 are **developer regression tests**.  
- They ensure solver correctness, integration, and stability.  
- Results should be reproducible.  
- Additional tests may be added for new features.  

