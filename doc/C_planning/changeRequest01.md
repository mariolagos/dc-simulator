# ChangeRequest01.md

**Type:** TechnicalDebt  
**Status:** Open  
**Title:** Refactor the test-building framework (remove reflection-based MiniGrid)  
**Author:** MLa  
**Date:** 2025-10-12

---

## Background

During recent test development, the `MiniGrid` test fixture evolved into a reflection-heavy framework for building small `GridModel` instances dynamically.  
While this approach allowed flexible testing across versions, it introduced **severe drawbacks**:

- Complex reflection chains for `Node`, `Real`, `Substation`, and `Line`.
- Hard-to-debug runtime errors (`NoSuchMethodException`, `InvocationTargetException`, `ClassNotFoundException`).
- No compile-time safety; any API drift breaks tests silently.
- High cognitive load for future maintainers.

The reflection-based test builder is now a source of **technical debt** and needs to be replaced with a clear, type-safe creation pattern.

---

## Lessons Learned

1. **Reflection = hidden complexity.**  
   Reflection abstracts away compile-time contracts, making debugging far harder than direct constructor calls.

2. **Verbose and deterministic > dynamic and magical.**  
   Test code should be explicit, short, and predictable, even if slightly more verbose.

3. **Single code path for production and tests.**  
   Builders and solvers used in tests must call the same core routines (`NetBuilder`, `DcLinearSolver`, etc.), with optional verbosity toggles, not bypass them.

4. **Creational patterns work better.**  
   Using a well-defined interface (`ModelAssembler`) and a test builder (`MiniGridLite`) gives:
    - Type safety
    - Explicit intent
    - Ease of evolution (add train, diode, etc.)
    - No need for dynamic method guessing

---

## Refactoring Concept

The new design will replace `MiniGrid` and related reflection logic with compile-time creation patterns:

### 1. `ModelAssembler` (Abstract Factory / Strategy)
Defines how to build model components:
```java
interface ModelAssembler {
    void ensureNodes(GridModel<?> gm, int maxNode);
    void addSubstation(GridModel<?> gm, int a, int b, double emfV, double rint, boolean allowBackfeed, String id);
    void addLine(GridModel<?> gm, int a, int b, double rOhm, String id);
}
```

- **ElectricAssembler:** Uses `org.dcsim.electric` package classes.
- **ApiAssembler (optional):** Builds via `org.dcsim.solver.api.*` and `NetBuilder`.

### 2. `MiniGridLite` (Builder)
A small fluent builder for constructing compact networks for unit tests:
```java
var model = MiniGridLite.with(new ElectricAssembler())
    .substation(1, 0, 900, 2, false, "SS")
    .line(1, 0, 8, "L10")
    .build();
```

### 3. Test Data Builders
Reusable "topology templates" (e.g., voltage divider, island separation, backfeed-blocked).

---

## Components to Deprecate / Remove

| Component | Action | Reason |
|------------|---------|--------|
| `MiniGrid.java` | **Remove** | Reflection-based, brittle |
| Reflection helpers (`ensureNodes`, `invokeAdd*`) | **Remove** | Dynamic method lookup is unstable |
| "Substation fallback stamping" | **Remove** | Should use proper stemping via assembler |
| Mixed device injection via reflection | **Remove** | Replace with typed creation methods |

---

## Replacement Components

| Component | Type | Description |
|------------|------|-------------|
| `ModelAssembler` | Interface | Defines model-building primitives |
| `ElectricAssembler` | Concrete class | Uses `org.dcsim.electric` API |
| `MiniGridLite` | Builder | Fluent test builder |
| `Topologies` | Utility | Predefined reusable networks |

---

## Migration Plan (Post-Release)

1. Introduce `ModelAssembler`, `ElectricAssembler`, and `MiniGridLite` in `src/testFixtures/java/org/dcsim/testing`.
2. Migrate reflection-based tests incrementally.
3. Add small “Topologies” utilities for recurring configurations.
4. Remove `MiniGrid.java` and all reflection-based logic.
5. Update `README_dev.md` to include new test-builder usage.
6. Confirm full coverage with verbose enabled for solver verification.

**Definition of Done:**
- No reflection used anywhere in test code.
- All `GridModel` test builds go through `ModelAssembler` + `MiniGridLite`.
- Verbosity flags correctly propagate to solvers.
- Documentation updated accordingly.

---

## Risks and Mitigation

| Risk | Mitigation |
|------|-------------|
| API drift in `org.dcsim.electric` | Use adapter inside assembler |
| Parallel builders (API vs Electric) | Keep one default (`ElectricAssembler`) |
| Temporary dual system | Migrate test groups gradually with clear naming |

---

## Status Summary

| Aspect | State |
|---------|--------|
| Implementation | ❌ Not started |
| Documentation | ✅ Complete |
| Tests impacted | ~10 direct, ~20 indirect |
| Blocking current build | ✅ Reflection-based MiniGrid |
| Priority | High (after release) |
