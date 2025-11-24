### Symphony Baseline Context  (added 2025-11-14)

This document operates under the **Symphony node-to-node DC baseline** established in documentation version 1.3.  
The Symphony baseline defines how the electrical, numerical, and planning layers align:

| Level | Key Document | Purpose |
|:--|:--|:--|
| **A** | `A_user/modelDescription.md` | Describes the physical model, assumptions, and observable behaviour. |
| **B** | `B_developer/softwareSpecification.md` | Formalises equations, boundary conditions, and solver architecture. |
| **C** | *(current document)* | Connects the model to planning, milestones, and verification logic. |

**Implications for this document**
- All planning, validation, and acceptance steps reference the *Symphony node-to-node network* and its critical-boundary framework.
- Numerical stability, event handling, and topological switching follow the definitions in the B-level specification.
- Future prototypes or deliverables must maintain compatibility with the Symphony baseline unless explicitly superseded by a new version.

> _Section added under MFE + Trace policy.  No previous content removed; inserted for alignment with updated documentation hierarchy (A < B < C)._  
