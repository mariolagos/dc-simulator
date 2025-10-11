# Documentation Plan (dcSimulator)
**Document Level: C (Planning)**  
**Purpose:** Define ownership, structure, and update rules for all project documentation.

---

## 1. Overview
This plan ensures consistency across all documentation levels in `dcSimulator`.  
Each feature or revision must update the corresponding document(s) according to the A–B–C hierarchy.

**Principle:**
> A < B < C (A-docs may be referenced by B, and both may be referenced by C, but never duplicated.)

---

## 2. Hierarchy Overview

| Level | Audience | Purpose | Main Documents |
|-------|-----------|----------|----------------|
| **A. User-level** | Operators, analysts | Explain usage, configuration, and models | `USER_GUIDE.md`, `modelDescription.md`, `terms.md` |
| **B. Developer-level** | Developers, maintainers | Explain architecture, APIs, tests, and coding practices | `softwareSpecification.md`, `README.md`, `README_utv.md`, `testPlan.md` |
| **C. Planning-level** | Project managers, architects | Track documentation, progress, and plans | `docPlan.md`, `prototypePlan.md`, `progressStatus.md`, `todo.md` |

---

## 3. File Structure

```
docs/
├── A_user/
│ ├── USER_GUIDE.md
│ ├── modelDescription.md
│ ├── terms.md
│ └── examples/
│
├── B_developer/
│ ├── softwareSpecification.md
│ ├── README.md
│ ├── README_utv.md
│ ├── testPlan.md
│ └── architecture/
│
└── C_planning/
├── docPlan.md
├── prototypePlan.md
├── progressStatus.md
└── todo.md
```

---

## 4. Update Protocol

Whenever a new feature or improvement is added:
1. **A:** Update `USER_GUIDE.md` (user impact) and `modelDescription.md` (if new physical models).
2. **B:** Update `README_utv.md` (story), `softwareSpecification.md` (traceability), and `testPlan.md` (new tests).
3. **C:** Add entry to `progressStatus.md` and, if major, `prototypePlan.md`.

> Each merge or release should include a doc review to ensure all levels remain consistent.

---

## 5. Cross-References

- `USER_GUIDE.md` → `terms.md`, `modelDescription.md`
- `README_utv.md` → `softwareSpecification.md`, `testPlan.md`
- `docPlan.md` → all C-level docs
- `progressStatus.md` → references prototypePlan.md for milestones

---

## 6. Future Enhancements
- Add auto-generation of document index in CI.
- Introduce document metadata headers (`Document Level`, `Owner`, `Last Updated`, `Related Docs`).
- Optional: publish docs via MkDocs or Sphinx for web viewing.

---

## 7. Version History
| Version | Date | Author | Change |
|----------|------|---------|--------|
| 0.1 | 2025-10-06 | System refactor | Introduced unified doc structure (A/B/C) |
| 0.2 | (planned) | – | Merge legacy tech docs → `modelDescription.md` |
| 0.3 | (planned) | – | Merge progress logs → `progressStatus.md` |
