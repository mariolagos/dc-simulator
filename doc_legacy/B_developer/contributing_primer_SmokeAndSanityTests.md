# Contributing Primer — Smoke & Sanity Tests
**Last updated:** 2025-10-14

This addendum complements the contributing primer with conventions for **smoke** and **sanity** tests. It follows our JUnit **4** setup and aligns with `testPlan.md` (§2.4, §3.3, §5.3).

---

## 1) Definitions (short)
- **Sanity tests:** very small, fast checks that a *specific unit or config* is coherent (e.g., a parser accepts a valid string; a required key exists). Goal: catch obvious mistakes **early**.
- **Smoke tests:** minimal **end‑to‑end** runs that exercise the main path (build → run → basic outputs). Goal: prove that “the system basically works” after integration.

> Rule of thumb: *sanity = focused + fast; smoke = end‑to‑end + minimal.*

---

## 2) Scope & Where They Live
```
src/test/java/...            # Sanity tests (JUnit 4, *Test)
src/test/java/...            # Ultra-light smoke variants (if they don't need full app)
src/it/java/...              # Smoke tests that spin up the full app (*IT)
baseline/<case>/*.csv        # Regression baselines (used by some smoke tests)
```

Naming suggestions:
- Sanity: `*SanityTest` (e.g., `ChainageParserSanityTest`)
- Smoke:  `*SmokeTest` or `{Case}SmokeTest` (e.g., `Tracks1_SmokeTest`, `ThreeSubsProfilesSmokeTest`)

Tagging (optional via categories): `@Category(Smoke.class)` / `@Category(Sanity.class)`

---

## 3) When to Run
- **Before commit (local):**
    - Sanity tests for any code you touched (parsers, config, builders, reporting schema).
    - One smoke test relevant to your area (e.g., `Tracks1_SmokeTest`).

- **In CI:**
    - **unit** job runs sanity tests (together with normal unit tests).
    - **integration** job runs smoke tests (*IT).
    - **regression** job may run selected smoke tests that compare outputs to `baseline/` (diff tolerated by §6 in `testPlan.md`).

---

## 4) Commands (JUnit 4)
**Gradle**
```
./gradlew test --tests "*SanityTest"
./gradlew integrationTest --tests "*SmokeTest"
```
**Maven**
```
mvn -q -Dtest="*SanityTest" test
mvn -q -DskipITs=false -Dit.test="*SmokeTest" verify
```

**IntelliJ**
- Run/Debug Config → JUnit → Pattern: `.*SanityTest` or `.*SmokeTest`

---

## 5) Authoring Guidelines
### 5.1 Sanity tests (fast & focused)
- One behavior per test; no external I/O if possible.
- Prefer table‑driven test data for parsers (valid/invalid pairs).
- Fail with clear messages (actionable error strings).

**Example checklist**
- [ ] Valid input passes (`S 12+345`, `12+000`, `0+50`)
- [ ] Invalid formats rejected (bad separators, negative km, overflow)
- [ ] Round‑trip meters ↔ chainage stable (if applicable)

### 5.2 Smoke tests (minimal end‑to‑end)
- Use the **smallest** config that still exercises the main path.
- Assert essential outputs only (e.g., csv exists + columns present).
- Keep runtime short (seconds). Offload heavy checks to regression tests.

**Example checklist**
- [ ] App starts and completes without errors
- [ ] Output folder created (`output/`)
- [ ] Long‑table CSV exists with columns `(time, object, signal, value)`
- [ ] Optional: energy balance within tolerance (if cheap to compute)

---

## 6) Tracks Variants (1 / 2 / 3+)
For topology‑dependent smoke tests, parametrize across tracks count:
- `Tracks1_SmokeTest` → single track isolation & expected voltage drop
- `Tracks2_SmokeTest` → double track isolation & sharing (crossover optional)
- `Tracks3_SmokeTest` → convergence + schema only (smoke)

Link to `testPlan.md §3.3` for the matrix and to R‑04/R‑08 in `softwareSpecification_v1_1.md`.

---

## 7) Failure Triage
- **Sanity failure** → fix or revert the change; these should never be flaky.
- **Smoke failure** → check config/seed; if schema changed intentionally, update the assertion or move the heavy check to regression.
- Record non‑obvious outcomes in `progressStatus.md` under “Risks & Mitigations (live)”.

---

## 8) Pull Request Checklist (extract)
- [ ] Sanity tests added/updated for new parsers/config/builders.
- [ ] A minimal smoke test passes locally for your area.
- [ ] CI: unit & integration jobs are green.
- [ ] Baselines untouched unless the change is **intentional** and justified in PR.
