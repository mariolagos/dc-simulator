# docPlan.md
**Document Level:** C (Planning)  
**Purpose:** Define the structure and hierarchy of all DcSimulator documentation.  
**Audience:** Developers, architects, and maintainers.

---

## 1. Purpose
This plan defines roles, hierarchy, and cross-references between all documentation components in the DcSimulator project.  
Each topic has a **single authoritative source** to avoid duplication; other docs **link** to that source.

---

## 2. Entry Points
- **README.md (repo root)** — Front-door overview for developers: what the project is, quickstart, and where docs live.
- **docs/** — All A/B/C level documentation (see hierarchy below).

> Note: Content from legacy `README.md` (v0.4) has been integrated into the new root README and A/B docs where applicable.

---

## 3. Hierarchy Overview

| Level | Audience | Purpose | Main Documents |
|------|-----------|----------|----------------|
| **A. User-level** | Operators, analysts | Usage, configuration, model understanding | `USER_GUIDE.md`, `modelDescription.md`, `terms.md` |
| **B. Developer-level** | Developers, maintainers | Architecture, APIs, implementation, tests | `README_dev.md`, `softwareSpecification.md`, `testPlan.md` |
| **C. Planning-level** | PM/Architects | Plans and project status | `docPlan.md`, `prototypePlan.md`, `progressStatus.md` |

**Hierarchy rule:** Documents may reference the same or **lower** levels; never upwards.

---

## 4. Directory Layout

```
README.md              (repo root entry point)

docs/
├─ A_user/
│  ├─ USER_GUIDE.md
│  ├─ modelDescription.md
│  └─ terms.md
├─ B_developer/
│  ├─ README_dev.md
│  ├─ softwareSpecification_v1_1.md
│  └─ testPlan.md
└─ C_planning/
   ├─ docPlan.md
   ├─ prototypePlan_v_1.md
   └─ progressStatus.md
```

---
## Note on Track Topologies
The system must support **both single-track and double-track** configurations. Prototype and acceptance criteria in `prototypePlan.md` explicitly cover both cases.

## 5. Document Roles

### A. User-level
Focus on system operation and model.  
Primary files: `USER_GUIDE.md`, `modelDescription.md`, `terms.md`.

### B. Developer-level
Focus on architecture, code practices, tests.  
Primary files: `README_dev.md`, `softwareSpecification.md`, `testPlan.md`.

### C. Planning-level
Focus on strategy, planning, status.  
Primary files: `docPlan.md`, `prototypePlan.md`, `progressStatus.md`.

---

## 6. Cross-References

| From | To | Purpose |
|------|----|----------|
| `README.md` (root) | A/B/C folders | Navigation / documentation map |
| `USER_GUIDE.md` | `modelDescription.md`, `terms.md` | Model logic & vocabulary |
| `README_dev.md` | `softwareSpecification.md`, `testPlan.md` | Implementation & verification |
| `docPlan.md` | All | Governance of hierarchy and completeness |

---

## 7. Maintenance Rules
- New docs must declare **Level (A/B/C)** and **Purpose** at the top.
- Keep **single-source** per topic; link instead of duplicating.
- Archive legacy content; summarize changes in `progressStatus.md`.
- Update cross-links when filenames or versions change.
- When physical or mathematical definitions evolve (e.g., Symphony model), update `modelDescription.md` and `softwareSpecification.md` before modifying this plan.

---

## 8. Quick Links
- **Root README:** `../README.md`
- **A_user:** `./A_user/USER_GUIDE.md`, `./A_user/modelDescription.md`, `./A_user/terms.md`
- **B_developer:** `./B_developer/README_dev.md`, `./B_developer/softwareSpecification_v1_1.md`, `./B_developer/testPlan.md`
- **C_planning:** `./C_planning/progressStatus.md`, `./C_planning/prototypePlan.md`

## 8. Quick Links
- **Root README:** `../README.md`
- **A_user:** `./A_user/USER_GUIDE.md`, `./A_user/modelDescription.md`, `./A_user/terms.md`
- **B_developer:** `./B_developer/README_dev.md`, `./B_developer/softwareSpecification.md`, `./B_developer/testPlan.md`
- **C_planning:** `./C_planning/progressStatus.md`, `./C_planning/prototypePlan.md`

---

_Last updated: 2025‑10‑10._


**Maintenance note:** Testframeworkets konventioner och körkommandon är single-source i 
`docs/B_developer/README_dev.md` och `docs/B_developer/testPlan.md`.

_Last updated: 2025-11-14 (MFE + Trace update)._

_Change summary (2025-11-14):  
Added §3.1 Symphony Documentation Context linking A/B levels to Symphony baseline.  
All existing sections retained; no structural changes._